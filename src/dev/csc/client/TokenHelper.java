package dev.csc.client;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class TokenHelper {
    public static class SessionTokenData {
        public String publicIp;
        public String lanIp;
        public int port;
        public String password;
        public long expiresAt;
        public int maxClients;
        public String sessionSecret;

        public SessionTokenData(String publicIp, String lanIp, int port, String password, long expiresAt, int maxClients, String sessionSecret) {
            this.publicIp = publicIp;
            this.lanIp = lanIp;
            this.port = port;
            this.password = password;
            this.expiresAt = expiresAt;
            this.maxClients = maxClients;
            this.sessionSecret = sessionSecret;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    public static String generateToken(String publicIp, String lanIp, int port, String password, int durationHours, int maxClients, String sessionSecret) {
        long expiresAt = System.currentTimeMillis() + ((long) durationHours * 3600 * 1000);
        String rawJson = String.format(
            "{\"ip\":\"%s\",\"lan\":\"%s\",\"port\":%d,\"pw\":\"%s\",\"exp\":%d,\"max\":%d,\"sec\":\"%s\"}",
            escapeJson(publicIp), escapeJson(lanIp), port, escapeJson(password), expiresAt, maxClients, escapeJson(sessionSecret)
        );

        String sig = hmacSha256(rawJson, sessionSecret);
        String fullPayload = rawJson.substring(0, rawJson.length() - 1) + String.format(",\"sig\":\"%s\"}", sig);
        
        String b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(fullPayload.getBytes(StandardCharsets.UTF_8));
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
        String pw = getField(json, "pw");
        String expStr = getField(json, "exp");
        String maxStr = getField(json, "max");
        String sec = getField(json, "sec");
        String sig = getField(json, "sig");

        if (ip == null || expStr == null || sig == null || sec == null) {
            throw new IllegalArgumentException("Invalid token format");
        }

        int port = portStr != null ? Integer.parseInt(portStr) : 49156;
        int maxClients = maxStr != null ? Integer.parseInt(maxStr) : 2;
        long exp = Long.parseLong(expStr);

        String rawJson = String.format(
            "{\"ip\":\"%s\",\"lan\":\"%s\",\"port\":%d,\"pw\":\"%s\",\"exp\":%d,\"max\":%d,\"sec\":\"%s\"}",
            escapeJson(ip), escapeJson(lan != null ? lan : ""), port, escapeJson(pw != null ? pw : ""), exp, maxClients, escapeJson(sec)
        );

        String expectedSig = hmacSha256(rawJson, sec);
        if (!expectedSig.equals(sig)) {
            throw new SecurityException("Token signature tampered or invalid");
        }

        SessionTokenData data = new SessionTokenData(ip, lan != null ? lan : "", port, pw != null ? pw : "", exp, maxClients, sec);
        if (data.isExpired()) {
            throw new IllegalStateException("Session token has expired");
        }

        return data;
    }

    public static String generateEphemeralSecret() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public static String getLocalLanIp() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            return socket.getLocalAddress().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    private static String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString().substring(0, 16);
        } catch (Exception e) {
            return "signature_error";
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
