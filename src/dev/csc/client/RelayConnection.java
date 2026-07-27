package dev.csc.client;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import javax.crypto.SecretKey;

/**
 * Hardened TCP client with ECDH Key Exchange and Encrypted Handshake.
 */
public class RelayConnection {
    private Socket socket;
    private BufferedWriter writer;
    private volatile boolean connected = false;
    private Thread readThread;
    private SecretKey ecdhKey;
    private final MessageCallback callback;

    public RelayConnection(MessageCallback callback) {
        this.callback = callback;
    }

    public CompletableFuture<Boolean> connectWithFallback(String publicHost, String lanHost, int port, String name, String password) {
        return CompletableFuture.supplyAsync(() -> {
            if (publicHost != null && !publicHost.isEmpty()) {
                LoggerHelper.info("ClientConnection", "Attempting connection to Public IP (" + publicHost + ":" + port + ")...");
                if (trySocketConnect(publicHost, port, name, password)) {
                    return true;
                }
            }

            if (lanHost != null && !lanHost.isEmpty() && !lanHost.equals(publicHost)) {
                LoggerHelper.info("ClientConnection", "Public IP failed. Falling back to LAN IP (" + lanHost + ":" + port + ")...");
                if (trySocketConnect(lanHost, port, name, password)) {
                    return true;
                }
            }

            if (!"127.0.0.1".equals(publicHost) && !"127.0.0.1".equals(lanHost)) {
                LoggerHelper.info("ClientConnection", "Falling back to Localhost (127.0.0.1:" + port + ")...");
                if (trySocketConnect("127.0.0.1", port, name, password)) {
                    return true;
                }
            }

            LoggerHelper.error("ClientConnection", "All connection attempts (Public IP, LAN IP, Localhost) failed.");
            return false;
        });
    }

    private boolean trySocketConnect(String host, int port, String name, String password) {
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 3000);
            writer = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            // 1. Generate Client EC KeyPair & perform ECDH Handshake
            ECDHHelper.ECDHKeyPair clientKeyPair = ECDHHelper.generateKeyPair();
            sendRaw("{\"type\":\"ecdh_init\",\"ecdh_pub\":\"" + clientKeyPair.publicKeyBase64 + "\"}");

            String serverKeyLine = reader.readLine();
            if (serverKeyLine == null) {
                disconnect();
                return false;
            }

            String serverPubKey = RelayServer.getField(serverKeyLine, "ecdh_pub");
            if (serverPubKey == null) {
                LoggerHelper.warn("ClientConnection", "ECDH handshake failed from " + host);
                disconnect();
                return false;
            }

            ecdhKey = ECDHHelper.deriveSharedSecret(clientKeyPair.privateKey, serverPubKey);
            LoggerHelper.info("ClientConnection", "ECDH Key Agreement established successfully!");

            // 2. Send AES-256-GCM Encrypted Auth Payload
            String escapedName = RelayServer.escapeJson(name);
            String escapedPw = RelayServer.escapeJson(password);
            String rawAuthJson = "{\"type\":\"auth\",\"name\":\"" + escapedName + "\",\"password\":\"" + escapedPw + "\"}";
            sendEncrypted(rawAuthJson);

            // 3. Read Encrypted Auth Response
            String encResponse = reader.readLine();
            if (encResponse == null) {
                disconnect();
                return false;
            }

            String response = CryptoHelper.decryptWithKey(encResponse, ecdhKey);
            String type = RelayServer.getField(response, "type");
            if ("auth_fail".equals(type)) {
                String reason = RelayServer.getField(response, "reason");
                LoggerHelper.warn("ClientConnection", "Auth failed on " + host + ": " + reason);
                callback.onEvent("auth_fail", "", reason != null ? reason : "Authentication failed");
                disconnect();
                return false;
            }

            if (!"auth_ok".equals(type)) {
                disconnect();
                return false;
            }

            connected = true;
            LoggerHelper.info("ClientConnection", "Authenticated via ECDH on " + host + ":" + port + "!");
            callback.onEvent("connected", name, "");

            readThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null && connected) {
                        String decLine = CryptoHelper.decryptWithKey(line, ecdhKey);
                        String msgType = RelayServer.getField(decLine, "type");
                        if ("msg".equals(msgType)) {
                            String sender = RelayServer.getField(decLine, "sender");
                            String text = RelayServer.getField(decLine, "text");
                            if (sender != null && text != null) {
                                callback.onEvent("msg", sender, text);
                            }
                        } else if ("system".equals(msgType)) {
                            String text = RelayServer.getField(decLine, "text");
                            if (text != null) {
                                callback.onEvent("system", "", text);
                            }
                        }
                    }
                } catch (IOException e) {
                    if (connected) {
                        LoggerHelper.warn("ClientConnection", "Connection lost: " + e.getMessage());
                        callback.onEvent("disconnected", "", "Connection lost");
                    }
                }
                connected = false;
            }, "CSC-Relay-Read");
            readThread.setDaemon(true);
            readThread.start();

            return true;
        } catch (Exception e) {
            LoggerHelper.warn("ClientConnection", "Connect failed to " + host + ":" + port + " (" + e.getMessage() + ")");
            disconnect();
            return false;
        }
    }

    public void sendMessage(String text) {
        if (connected && writer != null) {
            try {
                String rawMsgJson = "{\"type\":\"msg\",\"text\":\"" + RelayServer.escapeJson(text) + "\"}";
                sendEncrypted(rawMsgJson);
            } catch (Exception e) {
                LoggerHelper.error("ClientConnection", "Failed to send message: " + e.getMessage());
                callback.onEvent("error", "", "Send failed: " + e.getMessage());
            }
        }
    }

    private void sendRaw(String json) {
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

    private void sendEncrypted(String json) {
        if (ecdhKey != null) {
            String enc = CryptoHelper.encryptWithKey(json, ecdhKey);
            sendRaw(enc);
        } else {
            sendRaw(json);
        }
    }

    public boolean isConnected() { return connected; }

    public void disconnect() {
        connected = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        writer = null;
        socket = null;
    }

    @FunctionalInterface
    public interface MessageCallback {
        void onEvent(String type, String sender, String text);
    }
}
