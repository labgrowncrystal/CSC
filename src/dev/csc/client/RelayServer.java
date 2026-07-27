package dev.csc.client;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Built-in TCP relay server that runs inside Minecraft.
 */
public class RelayServer {
    private ServerSocket serverSocket;
    private final int port;
    private final String passwordHash;
    private final int maxClients;
    private final long expiresAt;
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private volatile boolean running = false;
    private Thread acceptThread;
    private final MessageCallback callback;

    public RelayServer(int port, String password, int maxClients, long expiresAt, MessageCallback callback) {
        this.port = port;
        this.callback = callback;
        this.passwordHash = password.isEmpty() ? "" : sha256(password);
        this.maxClients = maxClients;
        this.expiresAt = expiresAt;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        LoggerHelper.info("RelayServer", "Server started on port " + port + " (Max clients: " + maxClients + ")");

        acceptThread = new Thread(() -> {
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
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
                    ClientHandler handler = new ClientHandler(socket);
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
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
    }

    public boolean isRunning() {
        return running;
    }

    public int getClientCount() {
        return clients.size();
    }

    public int getMaxClients() {
        return maxClients;
    }

    private void broadcast(String senderName, String json) {
        for (ClientHandler c : clients) {
            if (!c.name.equals(senderName)) {
                c.send(json);
            }
        }
    }

    public void broadcastFromExternal(String senderName, String json) {
        for (ClientHandler c : clients) {
            c.send(json);
        }
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private BufferedWriter writer;
        private String name = "";
        private boolean authenticated = false;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            String remoteAddr = socket.getRemoteSocketAddress().toString();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

                String authLine = reader.readLine();
                if (authLine == null) return;

                String type = getField(authLine, "type");
                String pw = getField(authLine, "password");
                String n = getField(authLine, "name");

                if (!"auth".equals(type) || n == null || n.isEmpty()) {
                    send("{\"type\":\"auth_fail\",\"reason\":\"Invalid auth request\"}");
                    LoggerHelper.warn("RelayServer", "Auth fail from " + remoteAddr + ": Invalid auth payload");
                    return;
                }

                if (!passwordHash.isEmpty()) {
                    if (pw == null || !passwordHash.equals(sha256(pw))) {
                        send("{\"type\":\"auth_fail\",\"reason\":\"Wrong password\"}");
                        LoggerHelper.warn("RelayServer", "Auth fail for player '" + n + "' from " + remoteAddr + ": Wrong password");
                        callback.onEvent("auth_fail", n, "Wrong password");
                        return;
                    }
                }

                this.name = n;
                this.authenticated = true;
                clients.add(this);
                send("{\"type\":\"auth_ok\"}");
                LoggerHelper.info("RelayServer", "Player '" + name + "' authenticated successfully from " + remoteAddr);
                callback.onEvent("connected", name, "");

                broadcast(name, "{\"type\":\"system\",\"text\":\"" + escapeJson(name) + " joined the private chat\"}");

                String line;
                while ((line = reader.readLine()) != null && running) {
                    String msgType = getField(line, "type");
                    if ("msg".equals(msgType)) {
                        String text = getField(line, "text");
                        if (text != null) {
                            String outJson = "{\"type\":\"msg\",\"sender\":\"" + escapeJson(name) + "\",\"text\":\"" + escapeJson(text) + "\"}";
                            broadcast(name, outJson);
                            callback.onEvent("msg", name, text);
                        }
                    } else if ("ping".equals(msgType)) {
                        send("{\"type\":\"pong\"}");
                    }
                }
            } catch (IOException e) {
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

        void send(String json) {
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
