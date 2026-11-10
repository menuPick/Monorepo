package menupick;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** AI_BASE_URL이 없을 때(기본값): 규칙 엔진만 쓰고, AI 관리자 API는 꺼져 있음을 알려줍니다. */
class AiDisabledTest extends ApiTestSupport {
    @Autowired
    ObjectMapper json;

    @Test
    void reportsAiDisabledAndUsesH2() throws Exception {
        JsonNode health = json.readTree(send("GET", "/health", null).body());
        assertEquals("ok", health.get("status").asString());
        assertEquals("h2", health.get("storage").asString(), "local default storage is H2");

        HttpResponse<String> created = send("POST", "/api/recommendations",
                "{\"menuName\":\"국수\",\"reason\":\"따뜻한 국물\"}");
        assertStatus(201, created, "recommendation");
        long id = json.readTree(created.body()).get("id").asLong();

        String token = json.readTree(send("POST", "/api/admin/login", "{\"id\":\"" + ADMIN_ID + "\"}").body())
                .get("token").asString();
        JsonNode assessments = json.readTree(send("GET", "/api/admin/ai-assessments", null, null, token).body());
        assertFalse(assessments.get("enabled").asBoolean());
        assertEquals(0, assessments.get("items").size());

        HttpResponse<String> reassess = send("POST", "/api/admin/recommendations/" + id + "/ai-assessment", null, null, token);
        assertStatus(503, reassess, "AI disabled");
        assertEquals("ai_disabled", json.readTree(reassess.body()).get("error").asString());

        assertStatus(401, send("GET", "/api/admin/ai-assessments", null), "admin token required");

        HttpResponse<String> draft = send("POST", "/api/recommendations/reason-draft", "{\"reason\":\"추워요\"}");
        assertStatus(503, draft, "reason draft needs AI");
        assertEquals("ai_unavailable", json.readTree(draft.body()).get("error").asString());
    }
}
