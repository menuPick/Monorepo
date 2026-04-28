package menupick.model;

import java.util.LinkedHashMap;
import java.util.Map;

public final class InquiryRecord {
    private final long id;
    private final String message;
    private final String adminEmail;
    private final String createdAt;

    public InquiryRecord(long id, String message, String adminEmail, String createdAt) {
        this.id = id;
        this.message = message;
        this.adminEmail = adminEmail;
        this.createdAt = createdAt;
    }

    public long id() {
        return id;
    }

    public String message() {
        return message;
    }

    public String adminEmail() {
        return adminEmail;
    }

    public String createdAt() {
        return createdAt;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("message", message);
        map.put("adminEmail", adminEmail);
        map.put("createdAt", createdAt);
        return map;
    }

    public static InquiryRecord fromMap(Map<String, String> map) {
        long id = parseLong(map.get("id"));
        return new InquiryRecord(
                id,
                defaultString(map.get("message")),
                defaultString(map.get("adminEmail")),
                defaultString(map.get("createdAt"))
        );
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(defaultString(value));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }
}

