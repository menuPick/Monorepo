package menupick.recommendation;

import java.util.Locale;

/** 현재 운영을 위한 가벼운 fallback 추천 엔진입니다. */
public final class RuleBasedRecommendationEngine implements RecommendationEngine {
    @Override
    public String recommend(String menuName, String reason) {
        String safeMenuName = menuName == null ? "" : menuName;
        String source = (safeMenuName + " " + (reason == null ? "" : reason))
                .toLowerCase(Locale.ROOT);

        // 사용자가 메뉴명을 직접 입력했다면 이유의 일반 키워드보다 우선합니다.
        if (safeMenuName.contains("라면")) {
            return "라면";
        }
        if (source.contains("매운") || source.contains("떡볶") || source.contains("spicy")) {
            return "떡볶이";
        }
        if (source.contains("채소") || source.contains("야채") || source.contains("비빔")) {
            return "비빔밥";
        }
        if (source.contains("국") || source.contains("따뜻")) {
            return "김치찌개";
        }
        if (source.contains("면") || source.contains("noodle")) {
            return "잔치국수";
        }
        return "비빔밥";
    }
}
