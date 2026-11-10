package menupick.domain;

/** 추천 항목. JSON 필드명은 Flutter 앱과의 API 계약이므로 바꾸지 않습니다. */
public record Recommendation(
        long id,
        String menuName,
        String reason,
        String recommendedMenu,
        String category,
        String createdAt
) {
    public Recommendation {
        menuName = nullToEmpty(menuName);
        reason = nullToEmpty(reason);
        recommendedMenu = nullToEmpty(recommendedMenu);
        createdAt = nullToEmpty(createdAt);
        category = nullToEmpty(category);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
