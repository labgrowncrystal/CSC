package dev.csc.client;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.crypto.SecretKey;

/**
 * Hardened P2P TCP Relay Server v1.4.0
 * Features: ECDH Key Agreement, Encrypted Handshake, Constant-Time Auth, Message Rate-Limiting & IP Ban.
 */
public class RelayServer {
    private ServerSocket serverSocket;
    private final int port;
    private final String passwordHash;
    private final int maxClients;
    private final long expiresAt;
    private final ECDHHelper.ECDHKeyPair hostKeyPair;
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private final Map<String, Integer> failedAttempts = new ConcurrentHashMap<>();
    private final Map<String, Long> bannedIps = new ConcurrentHashMap<>();
    private volatile boolean running = false;
    private Thread acceptThread;
    private final MessageCallback callback;

    public RelayServer(int port, String password, int maxClients, long expiresAt, ECDHHelper.ECDHKeyPair hostKeyPair, MessageCallback callback) {
        this.port = port;
        this.callback = callback;
        this.passwordHash = password.isEmpty() ? "" : sha256(password);
        this.maxClients = maxClients;
        this.expiresAt = expiresAt;
        this.hostKeyPair = hostKeyPair;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        LoggerHelper.info("RelayServer", "Server started on port " + port + " (ECDH Enabled, Max clients: " + maxClients + ")");

        acceptThread = new Thread(() -> {
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    String remoteIp = getRemoteIp(socket);

                    Long banTime = bannedIps.get(remoteIp);
                    if (banTime != null) {
                        if (System.currentTimeMillis() < banTime) {
                            LoggerHelper.warn("RelayServer", "Rejected connection from banned IP: " + remoteIp);
                            socket.close();
                            continue;
                        } else {
                            bannedIps.remove(remoteIp);
                            failedAttempts.remove(remoteIp);
                        }
                    }

                    if (expiresAt > 0 && System.currentTimeMillis() > expiresAt) {
                        LoggerHelper.warn("RelayServer", "Rejected connection: Host session expired");
                        socket.close();
                        continue;
                    }
                    if (clients.size() >= maxClients) {
                        LoggerHelper.warn("RelayServer", "Rejected connection: Client limit reached (" + clients.size() + "/" + maxClients + ")");
                        socket.close();
                        continue;
                    }
                    ClientHandler handler = new ClientHandler(socket, remoteIp);
                    new Thread(handler).start();
                } catch (IOException e) {
                    if (running) {
                        LoggerHelper.error("RelayServer", "Accept error: " + e.getMessage());
                    }
                }
            }
        }, "CSC-Relay-Accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public void stop() {
        running = false;
        LoggerHelper.info("RelayServer", "Stopping server...");
        for (ClientHandler c : clients) {
            c.disconnect();
        }
        clients.clear();
        bannedIps.clear();
        failedAttempts.clear();
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
    }

    public boolean isRunning() { return running; }
    public int getClientCount() { return clients.size(); }
    public int getMaxClients() { return maxClients; }

    private void broadcast(String senderName, String json) {
        for (ClientHandler c : clients) {
            if (!c.name.equals(senderName)) {
                c.sendEncrypted(json);
            }
        }
    }

    public void broadcastFromExternal(String senderName, String json) {
        for (ClientHandler c : clients) {
            c.sendEncrypted(json);
        }
    }

    private String getRemoteIp(Socket socket) {
        try {
            InetSocketAddress addr = (InetSocketAddress) socket.getRemoteSocketAddress();
            return addr.getAddress().getHostAddress();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private final String remoteIp;
        private BufferedWriter writer;
        private String name = "";
        private boolean authenticated = false;
        private SecretKey ecdhKey;

        // Rate limiting: max 10 messages per second
        private int msgCount = 0;
        private long lastMsgResetTime = System.currentTimeMillis();

        ClientHandler(Socket socket, String remoteIp) {
            this.socket = socket;
            this.remoteIp = remoteIp;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

                // 1. First exchange ECDH Public Keys
                String clientKeyLine = readBoundedLine(reader);
                if (clientKeyLine == null) return;

                String clientPubKey = getField(clientKeyLine, "ecdh_pub");
                if (clientPubKey == null) {
                    LoggerHelper.warn("RelayServer", "Invalid ECDH handshake from " + remoteIp);
                    return;
                }

                // Send Host Public Key
                sendRaw("{\"type\":\"ecdh_init\",\"ecdh_pub\":\"" + hostKeyPair.publicKeyBase64 + "\"}");

                // Compute shared ECDH secret
                ecdhKey = ECDHHelper.deriveSharedSecret(hostKeyPair.privateKey, clientPubKey);

                // 2. Read encrypted Auth Line
                String encAuthLine = readBoundedLine(reader);
                if (encAuthLine == null) return;

                String authLine = CryptoHelper.decryptWithKey(encAuthLine, ecdhKey);
                String type = getField(authLine, "type");
                String pw = getField(authLine, "password");
                String n = getField(authLine, "name");

                if (!"auth".equals(type) || n == null || n.isEmpty()) {
                    sendEncrypted("{\"type\":\"auth_fail\",\"reason\":\"Invalid auth request\"}");
                    LoggerHelper.warn("RelayServer", "Auth fail from " + remoteIp + ": Invalid auth payload");
                    return;
                }

                // Password check using Constant-Time comparison to prevent timing attacks
                if (!passwordHash.isEmpty()) {
                    String providedPwHash = pw != null ? sha256(pw) : "";
                    if (!CryptoHelper.constantTimeEquals(passwordHash, providedPwHash)) {
                        sendEncrypted("{\"type\":\"auth_fail\",\"reason\":\"Wrong password\"}");
                        int fails = failedAttempts.getOrDefault(remoteIp, 0) + 1;
                        failedAttempts.put(remoteIp, fails);
                        LoggerHelper.warn("RelayServer", "Auth fail for player '" + n + "' from " + remoteIp + " (Attempt " + fails + "/5)");
                        
                        if (fails >= 5) {
                            long banUntil = System.currentTimeMillis() + (5 * 60 * 1000);
                            bannedIps.put(remoteIp, banUntil);
                            LoggerHelper.error("RelayServer", "Rate limit exceeded! Temporarily banned IP " + remoteIp + " for 5 minutes.");
                        }

                        callback.onEvent("auth_fail", n, "Wrong password");
                        return;
                    }
                }

                failedAttempts.remove(remoteIp);
                this.name = n;
                this.authenticated = true;
                clients.add(this);
                sendEncrypted("{\"type\":\"auth_ok\"}");
                LoggerHelper.info("RelayServer", "Player '" + name + "' authenticated via ECDH from " + remoteIp);
                callback.onEvent("connected", name, "");

                broadcast(name, "{\"type\":\"system\",\"text\":\"" + escapeJson(name) + " joined the private chat\"}");

                String line;
                while ((line = readBoundedLine(reader)) != null && running) {
                    // Check message rate limit (max 10 msgs / sec)
                    long now = System.currentTimeMillis();
                    if (now - lastMsgResetTime > 1000) {
                        msgCount = 0;
                        lastMsgResetTime = now;
                    }
                    msgCount++;
                    if (msgCount > 10) {
                        LoggerHelper.warn("RelayServer", "Message rate limit exceeded by " + name + " (" + remoteIp + ")");
                        continue; // Drop spam messages
                    }

                    // Decrypt incoming payload
                    String decLine = CryptoHelper.decryptWithKey(line, ecdhKey);
                    String msgType = getField(decLine, "type");
                    if ("msg".equals(msgType)) {
                        String text = getField(decLine, "text");
                        if (text != null) {
                            String outJson = "{\"type\":\"msg\",\"sender\":\"" + escapeJson(name) + "\",\"text\":\"" + escapeJson(text) + "\"}";
                            broadcast(name, outJson);
                            callback.onEvent("msg", name, text);
                        }
                    } else if ("ping".equals(msgType)) {
                        sendEncrypted("{\"type\":\"pong\"}");
                    }
                }
            } catch (Exception e) {
            } finally {
                clients.remove(this);
                if (authenticated) {
                    LoggerHelper.info("RelayServer", "Player '" + name + "' disconnected");
                    broadcast(name, "{\"type\":\"system\",\"text\":\"" + escapeJson(name) + " left the private chat\"}");
                    callback.onEvent("disconnected", name, "");
                }
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private String readBoundedLine(BufferedReader reader) throws IOException {
            StringBuilder sb = new StringBuilder();
            int ch;
            int count = 0;
            while ((ch = reader.read()) != -1) {
                if (ch == '\n') break;
                if (ch != '\r') {
                    sb.append((char) ch);
                    count++;
                    if (count > 16384) {
                        throw new IOException("Input line exceeded max size limit (16KB)");
                    }
                }
            }
            return sb.length() > 0 || ch != -1 ? sb.toString() : null;
        }

        void sendRaw(String json) {
            try {
                if (writer != null) {
                    synchronized (writer) {
                        writer.write(json);
                        writer.newLine();
                        writer.flush();
                    }
                }
            } catch (IOException ignored) {}
        }

        void sendEncrypted(String json) {
            if (ecdhKey != null) {
                String enc = CryptoHelper.encryptWithKey(json, ecdhKey);
                sendRaw(enc);
            } else {
                sendRaw(json);
            }
        }

        void disconnect() {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return input;
        }
    }

    static String getField(String json, String field) {
        String pattern = "\"" + field + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) return null;
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;
        return json.substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @FunctionalInterface
    public interface MessageCallback {
        void onEvent(String type, String sender, String text);
    }
}
