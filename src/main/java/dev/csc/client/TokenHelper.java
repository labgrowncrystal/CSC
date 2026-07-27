package dev.csc.client;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Encodes and parses CSC Session Tokens (CSC-XXXX...).
 * Security Upgrade: Session Tokens contain NO secrets, passwords, or private keys!
 * Only connection routing info (IP, Port, Exp, Max, Host EC Public Key).
 */
public class TokenHelper {

    public static class SessionTokenData {
        public String publicIp;
        public String lanIp;
        public int port;
        public long expiresAt;
        public int maxClients;
        public String hostPubKey;

        public SessionTokenData(String publicIp, String lanIp, int port, long expiresAt, int maxClients, String hostPubKey) {
            this.publicIp = publicIp;
            this.lanIp = lanIp;
            this.port = port;
            this.expiresAt = expiresAt;
            this.maxClients = maxClients;
            this.hostPubKey = hostPubKey;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    public static String generateToken(String publicIp, String lanIp, int port, int durationHours, int maxClients, String hostPubKey) {
        long expiresAt = System.currentTimeMillis() + ((long) durationHours * 3600 * 1000);
        String rawJson = String.format(
            "{\"ip\":\"%s\",\"lan\":\"%s\",\"port\":%d,\"exp\":%d,\"max\":%d,\"pub\":\"%s\"}",
            escapeJson(publicIp), escapeJson(lanIp), port, expiresAt, maxClients, escapeJson(hostPubKey)
        );

        String b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(rawJson.getBytes(StandardCharsets.UTF_8));
        return "CSC-" + b64;
    }

    public static SessionTokenData parseToken(String tokenStr) throws Exception {
        tokenStr = tokenStr.trim();
        if (tokenStr.startsWith("CSC-")) {
            tokenStr = tokenStr.substring(4);
        }

        byte[] decodedBytes = Base64.getUrlDecoder().decode(tokenStr);
        String json = new String(decodedBytes, StandardCharsets.UTF_8);

        String ip = getField(json, "ip");
        String lan = getField(json, "lan");
        String portStr = getField(json, "port");
        String expStr = getField(json, "exp");
        String maxStr = getField(json, "max");
        String pub = getField(json, "pub");

        if (ip == null || expStr == null || pub == null) {
            throw new IllegalArgumentException("Invalid token format");
        }

        int port = portStr != null ? Integer.parseInt(portStr) : 49156;
        int maxClients = maxStr != null ? Integer.parseInt(maxStr) : 2;
        long exp = Long.parseLong(expStr);

        SessionTokenData data = new SessionTokenData(ip, lan != null ? lan : "", port, exp, maxClients, pub);
        if (data.isExpired()) {
            throw new IllegalStateException("Session token has expired");
        }

        return data;
    }

    public static String getLocalLanIp() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            return socket.getLocalAddress().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    private static String getField(String json, String field) {
        String pattern = "\"" + field + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) return null;
        start += pattern.length();
        if (json.charAt(start) == '"') {
            start++;
            int end = json.indexOf("\"", start);
            if (end == -1) return null;
            return json.substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\");
        } else {
            int end = json.indexOf(",", start);
            if (end == -1) end = json.indexOf("}", start);
            if (end == -1) return null;
            return json.substring(start, end).trim();
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
