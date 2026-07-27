package dev.csc.client;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * TCP client that connects to a CSC RelayServer.
 * Supports automatic multi-IP fallback (Public IP -> LAN IP -> Localhost) for instant P2P/LAN connections.
 */
public class RelayConnection {
    private Socket socket;
    private BufferedWriter writer;
    private volatile boolean connected = false;
    private Thread readThread;
    private final MessageCallback callback;

    public RelayConnection(MessageCallback callback) {
        this.callback = callback;
    }

    public CompletableFuture<Boolean> connectWithFallback(String publicHost, String lanHost, int port, String name, String password) {
        return CompletableFuture.supplyAsync(() -> {
            // 1. Try public IP first
            if (publicHost != null && !publicHost.isEmpty()) {
                LoggerHelper.info("ClientConnection", "Attempting connection to Public IP (" + publicHost + ":" + port + ")...");
                if (trySocketConnect(publicHost, port, name, password)) {
                    return true;
                }
            }

            // 2. Try LAN IP if different
            if (lanHost != null && !lanHost.isEmpty() && !lanHost.equals(publicHost)) {
                LoggerHelper.info("ClientConnection", "Public IP connection failed. Falling back to LAN IP (" + lanHost + ":" + port + ")...");
                if (trySocketConnect(lanHost, port, name, password)) {
                    return true;
                }
            }

            // 3. Try Localhost if on same machine
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

    public CompletableFuture<Boolean> connect(String host, int port, String name, String password) {
        return connectWithFallback(host, "", port, name, password);
    }

    private boolean trySocketConnect(String host, int port, String name, String password) {
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 3000); // 3s timeout per IP
            writer = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            String escapedName = RelayServer.escapeJson(name);
            String escapedPw = RelayServer.escapeJson(password);
            writer.write("{\"type\":\"auth\",\"name\":\"" + escapedName + "\",\"password\":\"" + escapedPw + "\"}");
            writer.newLine();
            writer.flush();

            String response = reader.readLine();
            if (response == null) {
                disconnect();
                return false;
            }

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
            LoggerHelper.info("ClientConnection", "Connected & authenticated successfully via " + host + ":" + port + "!");
            callback.onEvent("connected", name, "");

            readThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null && connected) {
                        String msgType = RelayServer.getField(line, "type");
                        if ("msg".equals(msgType)) {
                            String sender = RelayServer.getField(line, "sender");
                            String text = RelayServer.getField(line, "text");
                            if (sender != null && text != null) {
                                callback.onEvent("msg", sender, text);
                            }
                        } else if ("system".equals(msgType)) {
                            String text = RelayServer.getField(line, "text");
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
        } catch (IOException e) {
            LoggerHelper.warn("ClientConnection", "Socket connect failed to " + host + ":" + port + " (" + e.getMessage() + ")");
            disconnect();
            return false;
        }
    }

    public void sendMessage(String text) {
        if (connected && writer != null) {
            try {
                synchronized (writer) {
                    writer.write("{\"type\":\"msg\",\"text\":\"" + RelayServer.escapeJson(text) + "\"}");
                    writer.newLine();
                    writer.flush();
                }
            } catch (IOException e) {
                LoggerHelper.error("ClientConnection", "Failed to send message: " + e.getMessage());
                callback.onEvent("error", "", "Send failed: " + e.getMessage());
            }
        }
    }

    public boolean isConnected() {
        return connected;
    }

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
