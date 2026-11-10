package menupick.recommendation;

import menupick.ai.AiClient;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** AI 서버가 설정돼 있으면 AI 추천, 없거나 실패하면 규칙 엔진 결과를 씁니다. */
@Primary
@Component
public class AiRecommendationEngine implements RecommendationEngine {
    private final AiClient ai;
    private final RuleBasedRecommendationEngine fallback;

    public AiRecommendationEngine(AiClient ai, RuleBasedRecommendationEngine fallback) {
        this.ai = ai;
        this.fallback = fallback;
    }

    @Override
    public String recommend(String menuName, String reason) {
        return ai.recommend(menuName, reason)
                .map(result -> result.recommendedMenu().strip())
                .map(menu -> menu.length() > 255 ? menu.substring(0, 255) : menu)
                .orElseGet(() -> fallback.recommend(menuName, reason));
    }
}
