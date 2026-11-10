package menupick.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuSafetyTest {
    @Test
    void blocksAlcoholTobaccoAndNonFood() {
        for (String menu : new String[]{"술", "술안주", "오늘은 술", "담배", "전자담배", "소주", "맥주", "하이볼",
                "소맥", "세제", "농약"}) {
            assertTrue(MenuSafety.containsBlockedWord(menu), menu);
        }
    }

    @Test
    void allowsNormalMenusIncludingWordsContainingSul() {
        for (String menu : new String[]{"비빔밥", "김치찌개", "기술가정 김밥", "미술시간 떡볶이", "수술 후 죽", "예술 도시락"}) {
            assertFalse(MenuSafety.containsBlockedWord(menu), menu);
        }
        assertFalse(MenuSafety.containsBlockedWord(null));
    }
}
