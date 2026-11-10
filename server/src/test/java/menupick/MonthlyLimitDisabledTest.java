package menupick;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** 로컬 개발용: 월 1회 제한을 끄면 같은 네트워크에서도 여러 번 올릴 수 있습니다. */
@TestPropertySource(properties = "menupick.rate-limit.monthly-recommendation=false")
class MonthlyLimitDisabledTest extends ApiTestSupport {

    @Test
    void allowsRepeatedRecommendationsWhenMonthlyLimitIsOff() throws Exception {
        String body = "{\"menuName\":\"비빔밥\",\"reason\":\"채소가 먹고 싶어요\"}";
        for (int i = 0; i < 3; i++) {
            assertStatus(201, send("POST", "/api/recommendations", body), "recommendation " + i);
        }
    }
}
