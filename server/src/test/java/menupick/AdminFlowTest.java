package menupick;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 관리자 로그인 → 목록 → 결정 메뉴 공개 → 다건 삭제 흐름. */
class AdminFlowTest extends ApiTestSupport {
    @Autowired
    ObjectMapper json;

    @Test
    void adminCanPublishDecisionAndDeleteMultipleItems() throws Exception {
        // 데이터 준비: 추천 1건(월 1회), 문의 3건
        HttpResponse<String> created = send("POST", "/api/recommendations",
                "{\"menuName\":\"\",\"reason\":\"매운 게 먹고 싶어요\",\"recommendedMenu\":\"\",\"category\":\"\"}");
        assertStatus(400, created, "menuName required");
        created = send("POST", "/api/recommendations",
                "{\"menuName\":\"떡볶이\",\"reason\":\"매운 게 먹고 싶어요\",\"recommendedMenu\":\"\",\"category\":\"\"}");
        assertStatus(201, created, "recommendation");
        JsonNode recommendation = json.readTree(created.body());
        assertEquals("떡볶이", recommendation.get("recommendedMenu").asString());
        long recommendationId = recommendation.get("id").asLong();

        for (int i = 0; i < 3; i++) {
            assertStatus(201, send("POST", "/api/inquiries", "{\"message\":\"문의 " + i + "\"}"), "inquiry " + i);
        }

        // 결정 설정이 없으면 최신 추천이 공개됩니다.
        assertStatus(200, send("GET", "/api/recommendations/latest", null), "latest without decision");

        // 로그인
        HttpResponse<String> login = send("POST", "/api/admin/login", "{\"id\":\"" + ADMIN_ID + "\"}");
        assertStatus(200, login, "admin login");
        JsonNode session = json.readTree(login.body());
        String token = session.get("token").asString();
        assertEquals("admin@local", session.get("adminEmail").asString());
        Instant.parse(session.get("expiresAt").asString());

        // 목록
        HttpResponse<String> inquiries = send("GET", "/api/admin/inquiries?limit=2", null, null, token);
        assertStatus(200, inquiries, "inquiry list");
        JsonNode items = json.readTree(inquiries.body()).get("items");
        assertEquals(2, items.size(), "limit applied");
        assertEquals("문의 2", items.get(0).get("message").asString(), "newest first");

        HttpResponse<String> latestInquiry = send("GET", "/api/inquiries/latest", null, null, token);
        assertStatus(200, latestInquiry, "latest inquiry");

        // 공개 시각이 미래면 not_published
        String future = Instant.now().plus(1, ChronoUnit.DAYS).toString();
        assertStatus(200, send("POST", "/api/admin/decision",
                "{\"recommendationId\":" + recommendationId + ",\"publishAt\":\"" + future + "\"}", null, token), "future decision");
        HttpResponse<String> hidden = send("GET", "/api/recommendations/latest", null);
        assertStatus(404, hidden, "not yet published");
        assertEquals("not_published", json.readTree(hidden.body()).get("error").asString());

        // 과거 시각이면 즉시 공개(캐시도 바로 갱신)
        String past = Instant.now().minus(1, ChronoUnit.MINUTES).toString();
        assertStatus(200, send("POST", "/api/admin/decision",
                "{\"recommendationId\":" + recommendationId + ",\"publishAt\":\"" + past + "\"}", null, token), "past decision");
        HttpResponse<String> shown = send("GET", "/api/recommendations/latest", null);
        assertStatus(200, shown, "published");
        assertEquals(recommendationId, json.readTree(shown.body()).get("id").asLong());

        assertStatus(404, send("POST", "/api/admin/decision",
                "{\"recommendationId\":9999,\"publishAt\":\"" + past + "\"}", null, token), "unknown recommendation");
        assertStatus(400, send("POST", "/api/admin/decision",
                "{\"recommendationId\":1,\"publishAt\":\"tomorrow\"}", null, token), "invalid publishAt");

        // 다건 삭제 (Flutter 형식 {"ids":[...]})
        HttpResponse<String> deleted = send("POST", "/api/admin/inquiries", "{\"ids\":[1,2,3]}", null, token);
        assertStatus(200, deleted, "delete inquiries");
        assertEquals(3, json.readTree(deleted.body()).get("deleted").asInt());

        // 결정 메뉴를 삭제하면 결정 설정도 사라져 공개 메뉴가 없어집니다.
        HttpResponse<String> deletedRecommendations = send("DELETE", "/api/admin/recommendations",
                "{\"ids\":[" + recommendationId + "]}", null, token);
        assertEquals(1, json.readTree(deletedRecommendations.body()).get("deleted").asInt());
        assertStatus(404, send("GET", "/api/recommendations/latest", null), "decision removed with recommendation");
    }
}
