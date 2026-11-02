package menupick.recommendation;

/**
 * 추천 결과를 만드는 경계입니다.
 *
 * 현재는 로컬 규칙 엔진을 사용하지만, 나중에는 이 인터페이스를 구현한
 * AI API 클라이언트로 교체할 수 있습니다. HTTP 핸들러는 구현체를 알지 않습니다.
 */
public interface RecommendationEngine {
    String recommend(String menuName, String reason);
}
