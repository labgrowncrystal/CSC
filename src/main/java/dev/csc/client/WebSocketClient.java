package com.example.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;

public class WebSocketClient {
    private WebSocket webSocket;
    private final MessageCallback callback;
    private final StringBuilder messageBuffer = new StringBuilder();

    public WebSocketClient(MessageCallback callback) {
        this.callback = callback;
    }

    public CompletableFuture<Void> connect(String url, String userId) {
        HttpClient client = HttpClient.newHttpClient();
        return client.newWebSocketBuilder()
            .buildAsync(URI.create(url), new WebSocket.Listener() {
                @Override
                public void onOpen(WebSocket ws) {
                    WebSocketClient.this.webSocket = ws;
                    String regJson = String.format("{\"type\":\"register\",\"user_id\":\"%s\"}", userId);
                    ws.sendText(regJson, true);
                    System.out.println("[ClientChat] Registered to relay as: " + userId);
                    ws.request(1);
                }

                @Override
                public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                    messageBuffer.append(data);
                    if (last) {
                        try {
                            callback.onMessageReceived(messageBuffer.toString());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        messageBuffer.setLength(0);
                    }
                    ws.request(1);
                    return null;
                }

                @Override
                public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                    System.out.println("[ClientChat] Connection closed: " + reason);
                    WebSocketClient.this.webSocket = null;
                    return null;
                }

                @Override
                public void onError(WebSocket ws, Throwable error) {
                    System.err.println("[ClientChat] WebSocket Error:");
                    error.printStackTrace();
                    WebSocketClient.this.webSocket = null;
                }
            }).thenAccept(ws -> {});
    }

    public void sendMessage(String recipientId, String messageText) {
        if (webSocket != null) {
            String escapedMsg = messageText.replace("\\", "\\\\").replace("\"", "\\\"");
            String msgJson = String.format(
                "{\"type\":\"message\",\"recipient_id\":\"%s\",\"payload\":\"%s\",\"msg_id\":\"%s\"}",
                recipientId, escapedMsg, java.util.UUID.randomUUID().toString()
            );
            webSocket.sendText(msgJson, true);
        }
    }

    public boolean isConnected() {
        return webSocket != null;
    }

    public void disconnect() {
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Disconnecting");
            webSocket = null;
        }
    }

    public interface MessageCallback {
        void onMessageReceived(String rawJson);
    }
}
