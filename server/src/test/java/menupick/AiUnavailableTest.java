package menupick;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.ServerSocket;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AI 서버가 설정돼 있지만 꺼져 있을 때: 사용자 저장은 규칙 엔진으로 계속 되고, 관리자 재판별은 502. */
class AiUnavailableTest extends ApiTestSupport {
    @DynamicPropertySource
    static void ai(DynamicPropertyRegistry registry) {
        registry.add("menupick.ai.base-url", () -> "http://127.0.0.1:" + closedPort());
        registry.add("menupick.ai.timeout", () -> "2s");
    }

    @Autowired
    ObjectMapper json;

    @Test
    void fallsBackToRulesWhenAiServerIsDown() throws Exception {
        HttpResponse<String> created = send("POST", "/api/recommendations",
                "{\"menuName\":\"아무거나\",\"reason\":\"매운 게 먹고 싶어요\"}");
        assertStatus(201, created, "saved even though AI is down");
        JsonNode recommendation = json.readTree(created.body());
        assertEquals("떡볶이", recommendation.get("recommendedMenu").asString(), "rule engine result");
        long id = recommendation.get("id").asLong();

        String token = login();
        JsonNode assessments = json.readTree(send("GET", "/api/admin/ai-assessments", null, null, token).body());
        assertTrue(assessments.get("enabled").asBoolean(), "configured");
        assertTrue(assessments.get("items").isEmpty(), "nothing stored when AI fails");

        HttpResponse<String> reassess = send("POST", "/api/admin/recommendations/" + id + "/ai-assessment", null, null, token);
        assertStatus(502, reassess, "AI down");
        assertEquals("ai_unavailable", json.readTree(reassess.body()).get("error").asString());
    }

    private String login() throws Exception {
        return json.readTree(send("POST", "/api/admin/login", "{\"id\":\"" + ADMIN_ID + "\"}").body())
                .get("token").asString();
    }

    /** 잠깐 열었다 닫은 포트 = 아무도 듣지 않는 주소. */
    private static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
