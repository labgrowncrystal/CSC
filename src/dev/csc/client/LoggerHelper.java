package dev.csc.client;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Privacy-focused Logger for CSC.
 * Features: Universal Regex IP Anonymization, Token Masking, and Log File Size Rotation (Max 250 KB).
 */
public class LoggerHelper {
    private static final Path LOG_DIR = Paths.get(
        System.getProperty("user.home"),
        "AppData", "Roaming", ".minecraft", "csc", "logs"
    );
    private static final Path LOG_FILE = LOG_DIR.resolve("csc-latest.log");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long MAX_LOG_SIZE = 250 * 1024; // 250 KB Max

    static {
        try {
            Files.createDirectories(LOG_DIR);
            rotateLogIfNeeded();
            log("INFO", "System", "=== CSC Logger Initialized (v1.4.4 Complete Leak Protection) ===");
        } catch (Exception e) {
            System.err.println("[CSC Logger] Failed to initialize logger: " + e.getMessage());
        }
    }

    private static void rotateLogIfNeeded() {
        try {
            if (Files.exists(LOG_FILE) && Files.size(LOG_FILE) > MAX_LOG_SIZE) {
                Path backup = LOG_DIR.resolve("csc-old.log");
                Files.move(LOG_FILE, backup, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignored) {}
    }

    public static synchronized void log(String level, String component, String message) {
        rotateLogIfNeeded();
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        String anonymizedMsg = anonymizeSensitiveData(message);
        String formatted = String.format("[%s] [%s/%s] %s", timestamp, level, component, anonymizedMsg);
        
        System.out.println(formatted);
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                Files.newOutputStream(LOG_FILE, StandardOpenOption.CREATE, StandardOpenOption.APPEND),
                StandardCharsets.UTF_8))) {
            writer.println(formatted);
        } catch (Exception ignored) {}
    }

    public static String anonymizeIp(String ip) {
        if (ip == null || ip.isEmpty() || "127.0.0.1".equals(ip) || "unknown".equals(ip)) {
            return ip;
        }
        int lastDot = ip.lastIndexOf('.');
        if (lastDot != -1) {
            return ip.substring(0, lastDot) + ".***";
        }
        int lastColon = ip.lastIndexOf(':');
        if (lastColon != -1) {
            return ip.substring(0, lastColon) + ":****";
        }
        return "***.***.***.***";
    }

    public static String maskToken(String token) {
        if (token == null || token.isEmpty()) return token;
        if (token.startsWith("CSC-")) {
            return token.substring(0, Math.min(12, token.length())) + "...[REDACTED_PRIVACY]";
        }
        return "[REDACTED_PRIVACY]";
    }

    public static String anonymizeSensitiveData(String msg) {
        if (msg == null) return "";
        // Mask tokens in log strings
        if (msg.contains("CSC-")) {
            int idx = msg.indexOf("CSC-");
            int endIdx = msg.indexOf(" ", idx);
            if (endIdx == -1) endIdx = msg.length();
            String rawToken = msg.substring(idx, endIdx);
            msg = msg.replace(rawToken, maskToken(rawToken));
        }
        // Universal Regex Mask for any IPv4 address (e.g. 192.168.1.100 -> 192.168.1.***)
        msg = msg.replaceAll("\\b((?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.)(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b", "$1***");
        return msg;
    }

    public static void info(String component, String message) { log("INFO", component, message); }
    public static void warn(String component, String message) { log("WARN", component, message); }
    public static void error(String component, String message) { log("ERROR", component, message); }
    public static Path getLogFile() { return LOG_FILE; }
}
