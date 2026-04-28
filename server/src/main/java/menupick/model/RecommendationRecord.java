package menupick.model;

import java.util.LinkedHashMap;
import java.util.Map;

public final class RecommendationRecord {
    private final long id;
    private final String menuName;
    private final String reason;
    private final String recommendedMenu;
    private final String createdAt;

    public RecommendationRecord(long id, String menuName, String reason, String recommendedMenu, String createdAt) {
        this.id = id;
        this.menuName = menuName;
        this.reason = reason;
        this.recommendedMenu = recommendedMenu;
        this.createdAt = createdAt;
    }

    public long id() {
        return id;
    }

    public String menuName() {
        return menuName;
    }

    public String reason() {
        return reason;
    }

    public String recommendedMenu() {
        return recommendedMenu;
    }

    public String createdAt() {
        return createdAt;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("menuName", menuName);
        map.put("reason", reason);
        map.put("recommendedMenu", recommendedMenu);
        map.put("createdAt", createdAt);
        return map;
    }

    public static RecommendationRecord fromMap(Map<String, String> map) {
        long id = parseLong(map.get("id"));
        return new RecommendationRecord(
                id,
                defaultString(map.get("menuName")),
                defaultString(map.get("reason")),
                defaultString(map.get("recommendedMenu")),
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

