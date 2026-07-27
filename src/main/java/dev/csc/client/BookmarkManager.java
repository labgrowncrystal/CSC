package dev.csc.client;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages saved server/token bookmarks stored in %APPDATA%/.minecraft/csc/bookmarks.json
 */
public class BookmarkManager {
    private static final File BOOKMARK_FILE = new File(LoggerHelper.getCscDir(), "bookmarks.json");
    private static final Map<String, Bookmark> bookmarks = new ConcurrentHashMap<>();

    public static class Bookmark {
        public String name;
        public String target; // Token or IP
        public String password;

        public Bookmark(String name, String target, String password) {
            this.name = name;
            this.target = target;
            this.password = password != null ? password : "";
        }
    }

    static {
        loadBookmarks();
    }

    public static synchronized void loadBookmarks() {
        bookmarks.clear();
        if (!BOOKMARK_FILE.exists()) return;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(BOOKMARK_FILE), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String json = sb.toString().trim();
            if (json.startsWith("[") && json.endsWith("]")) {
                json = json.substring(1, json.length() - 1);
                String[] entries = json.split("\\},\\{");
                for (String entry : entries) {
                    entry = entry.replace("{", "").replace("}", "");
                    String name = getField(entry, "name");
                    String target = getField(entry, "target");
                    String password = getField(entry, "password");
                    if (name != null && target != null) {
                        bookmarks.put(name.toLowerCase(), new Bookmark(name, target, password));
                    }
                }
            }
        } catch (Exception e) {
            LoggerHelper.error("BookmarkManager", "Failed to load bookmarks: " + e.getMessage());
        }
    }

    public static synchronized void saveBookmarks() {
        try {
            if (!BOOKMARK_FILE.getParentFile().exists()) {
                BOOKMARK_FILE.getParentFile().mkdirs();
            }
            StringBuilder sb = new StringBuilder("[\n");
            int idx = 0;
            for (Bookmark bm : bookmarks.values()) {
                sb.append("  {\"name\":\"").append(escapeJson(bm.name))
                  .append("\",\"target\":\"").append(escapeJson(bm.target))
                  .append("\",\"password\":\"").append(escapeJson(bm.password)).append("\"}");
                if (++idx < bookmarks.size()) sb.append(",");
                sb.append("\n");
            }
            sb.append("]");

            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(BOOKMARK_FILE), StandardCharsets.UTF_8))) {
                writer.write(sb.toString());
            }
            LoggerHelper.info("BookmarkManager", "Saved " + bookmarks.size() + " bookmarks to bookmarks.json");
        } catch (Exception e) {
            LoggerHelper.error("BookmarkManager", "Failed to save bookmarks: " + e.getMessage());
        }
    }

    public static boolean addBookmark(String name, String target, String password) {
        if (name == null || name.trim().isEmpty() || target == null || target.trim().isEmpty()) return false;
        bookmarks.put(name.trim().toLowerCase(), new Bookmark(name.trim(), target.trim(), password));
        saveBookmarks();
        return true;
    }

    public static boolean removeBookmark(String name) {
        if (name == null) return false;
        Bookmark removed = bookmarks.remove(name.trim().toLowerCase());
        if (removed != null) {
            saveBookmarks();
            return true;
        }
        return false;
    }

    public static Bookmark getBookmark(String name) {
        if (name == null) return null;
        return bookmarks.get(name.trim().toLowerCase());
    }

    public static Map<String, Bookmark> getAllBookmarks() {
        return Collections.unmodifiableMap(bookmarks);
    }

    private static String getField(String str, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = str.indexOf(pattern);
        if (start == -1) return null;
        start += pattern.length();
        int end = str.indexOf("\"", start);
        if (end == -1) return null;
        return str.substring(start, end);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
