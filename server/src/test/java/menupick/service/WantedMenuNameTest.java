package menupick.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WantedMenuNameTest {
    @Test
    void recognisesMenuNames() {
        for (String menu : new String[]{"비빔밥", "김치찌개", "치킨 마요 덮밥", "Pasta", "떡볶이(순한맛)", "돈까스&카레"}) {
            assertTrue(WantedMenu.isMenuName(menu), menu);
        }
    }

    @Test
    void rejectsNonMenuInput() {
        for (String input : new String[]{"", "   ", "메뉴명 미입력", "아무거나", "상관없음", "!!!", "__);_테스트_(_;",
                "가".repeat(41)}) {
            assertFalse(WantedMenu.isMenuName(input), input);
        }
        assertFalse(WantedMenu.isMenuName(null));
    }
}
