package menupick;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 사용자가 원하는 메뉴를 적으면 그 메뉴가 그대로 추천 메뉴가 됩니다.
 * (예: 비빔밥을 적었는데 AI가 김치찌개로 바꾸던 문제)
 */
@TestPropertySource(properties = "menupick.rate-limit.monthly-recommendation=false")
class WantedMenuTest extends ApiTestSupport {
    @Autowired
    ObjectMapper json;

    private String recommend(String menuName, String reason) throws Exception {
        var response = send("POST", "/api/recommendations",
                "{\"menuName\":\"" + menuName + "\",\"reason\":\"" + reason + "\"}");
        assertStatus(201, response, menuName);
        return json.readTree(response.body()).get("recommendedMenu").asString();
    }

    @Test
    void keepsTheMenuTheUserAskedFor() throws Exception {
        assertEquals("비빔밥", recommend("비빔밥", "야채등과 섞으면 맛있지 않을까?"));
        assertEquals("신라면", recommend(" 신라면 ", "매운 거"), "trimmed, not changed to 라면");
        assertEquals("소주", recommend("소주", "그냥"), "risky menus are kept and flagged by AI, not rewritten");
    }

    @Test
    void picksAMenuOnlyWhenNoneWasGiven() throws Exception {
        assertEquals("떡볶이", recommend("메뉴명 미입력", "매운 게 먹고 싶어요"), "app placeholder");
        assertEquals("김치찌개", recommend("아무거나", "따뜻한 국물"));
        assertEquals("비빔밥", recommend("!!!", "아무 이유"), "not a menu name");
    }
}
