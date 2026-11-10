package menupick.service;

import menupick.ai.AiClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 사용자가 적은 "원하는 메뉴"를 그대로 써도 되는지 판단합니다.
 * 위험하면(술·담배·음식 아님·부적절) 그 메뉴를 쓰지 않고 AI가 이유를 보고 대체 메뉴를 고르게 합니다.
 *
 * 1) 서버 금지어(AI 서버가 꺼져 있어도 동작)
 * 2) AI 위험 판별(그래프 엔진이면 밀리초 단위)
 */
@Component
public class MenuSafety {
    /** 이 플래그가 붙으면 급식 메뉴로 쓸 수 없습니다. (allergen·unhealthy는 메뉴는 유지하고 관리자에게만 표시) */
    private static final Set<String> BLOCKING_FLAGS = Set.of("not_food", "inappropriate", "prompt_injection");

    /** 메뉴 이름에 들어 있으면 안 되는 말(부분 일치). */
    private static final List<String> BLOCKED_WORDS = List.of(
            "담배", "전자담배", "시가", "소주", "맥주", "양주", "와인", "막걸리", "위스키", "보드카", "사케", "고량주",
            "칵테일", "하이볼", "샴페인", "소맥", "세제", "농약", "독극물", "독버섯", "플라스틱", "벌레", "약물",
            "시발", "씨발", "병신", "개새", "존나", "ㅅㅂ");

    /** '술'은 "기술·미술·수술"과 겹치므로 낱말이 '술'로 시작할 때만 막습니다(술, 술안주, 술집). */
    private static final String ALCOHOL_PREFIX = "술";

    private final AiClient ai;

    public MenuSafety(AiClient ai) {
        this.ai = ai;
    }

    public boolean isSafeMenu(String menuName) {
        if (containsBlockedWord(menuName)) {
            return false;
        }
        return ai.assess(menuName, "", menuName)
                .map(result -> !"high".equals(result.riskLevel())
                        && (result.flags() == null || result.flags().stream().noneMatch(BLOCKING_FLAGS::contains)))
                .orElse(true); // AI 판별 실패 시에는 서버 금지어 결과만 따릅니다.
    }

    static boolean containsBlockedWord(String menuName) {
        if (menuName == null) {
            return false;
        }
        String text = menuName.toLowerCase(Locale.ROOT);
        for (String word : BLOCKED_WORDS) {
            if (text.contains(word)) {
                return true;
            }
        }
        for (String token : text.split("\\s+")) {
            if (token.startsWith(ALCOHOL_PREFIX)) {
                return true;
            }
        }
        return false;
    }
}
