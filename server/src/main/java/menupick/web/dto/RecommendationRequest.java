package menupick.web.dto;

/** recommendedMenu가 비어 있으면 서버가 추천 메뉴를 만듭니다. */
public record RecommendationRequest(String menuName, String reason, String recommendedMenu, String category) {
}
