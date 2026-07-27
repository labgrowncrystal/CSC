package dev.csc.client;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Dedicated logger for CSC.
 * Saves logs to %APPDATA%/.minecraft/csc/logs/csc-latest.log
 */
public class LoggerHelper {
    private static final Path LOG_DIR = Paths.get(
        System.getProperty("user.home"),
        "AppData", "Roaming", ".minecraft", "csc", "logs"
    );
    private static final Path LOG_FILE = LOG_DIR.resolve("csc-latest.log");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static PrintWriter writer;

    static {
        try {
            Files.createDirectories(LOG_DIR);
            writer = new PrintWriter(new OutputStreamWriter(
                Files.newOutputStream(LOG_FILE, StandardOpenOption.CREATE, StandardOpenOption.APPEND),
                StandardCharsets.UTF_8
            ), true);
            log("INFO", "System", "=== CSC Logger Initialized ===");
        } catch (Exception e) {
            System.err.println("[CSC Logger] Failed to initialize logger: " + e.getMessage());
        }
    }

    public static synchronized void log(String level, String component, String message) {
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        String formatted = String.format("[%s] [%s/%s] %s", timestamp, level, component, message);
        
        System.out.println(formatted);
        if (writer != null) {
            writer.println(formatted);
        }
    }

    public static void info(String component, String message) {
        log("INFO", component, message);
    }

    public static void warn(String component, String message) {
        log("WARN", component, message);
    }

    public static void error(String component, String message) {
        log("ERROR", component, message);
    }

    public static Path getLogFile() {
        return LOG_FILE;
    }
}
