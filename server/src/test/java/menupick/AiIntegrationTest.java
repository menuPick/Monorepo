package menupick;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 가짜 AI 서버를 띄워 Java ↔ Python AI 계약(ai/app/schemas.py)을 검증합니다. */
class AiIntegrationTest extends ApiTestSupport {
    static final AtomicReference<String> seenToken = new AtomicReference<>();
    static final HttpServer fakeAi = startFakeAi();

    @DynamicPropertySource
    static void ai(DynamicPropertyRegistry registry) {
        registry.add("menupick.ai.base-url", () -> "http://127.0.0.1:" + fakeAi.getAddress().getPort());
        registry.add("menupick.ai.token", () -> "shared-secret");
    }

    @AfterAll
    static void stopFakeAi() {
        fakeAi.stop(0);
    }

    @Autowired
    ObjectMapper json;

    @Test
    void aiRecommendsAndAssessmentIsStoredForAdmin() throws Exception {
        HttpResponse<String> created = send("POST", "/api/recommendations",
                "{\"menuName\":\"메뉴명 미입력\",\"reason\":\"그냥\",\"recommendedMenu\":\"\"}");
        assertStatus(201, created, "recommendation");
        JsonNode recommendation = json.readTree(created.body());
        assertEquals("AI 김치볶음밥", recommendation.get("recommendedMenu").asString(),
                "AI picks a menu when the user gave none");
        long id = recommendation.get("id").asLong();

        String token = json.readTree(send("POST", "/api/admin/login", "{\"id\":\"" + ADMIN_ID + "\"}").body())
                .get("token").asString();

        // 판별은 백그라운드에서 끝납니다.
        JsonNode body = null;
        for (int i = 0; i < 50; i++) {
            body = json.readTree(send("GET", "/api/admin/ai-assessments", null, null, token).body());
            if (!body.get("items").isEmpty()) {
                break;
            }
            Thread.sleep(100);
        }
        assertTrue(body.get("enabled").asBoolean());
        JsonNode item = body.get("items").get(0);
        assertEquals(id, item.get("recommendationId").asLong());
        assertEquals("high", item.get("riskLevel").asString());
        assertEquals("not_food", item.get("flags").get(0).asString());
        assertEquals("shared-secret", seenToken.get(), "token forwarded");

        // AI는 아무것도 지우지 않습니다. 추천은 그대로 남아 있습니다.
        assertEquals(1, json.readTree(send("GET", "/api/admin/recommendations", null, null, token).body())
                .get("items").size());

        assertStatus(200, send("POST", "/api/admin/recommendations/" + id + "/ai-assessment", null, null, token), "reassess");
        assertStatus(404, send("POST", "/api/admin/recommendations/999/ai-assessment", null, null, token), "missing");

        // 추천을 지우면 판별 결과도 지워집니다.
        assertStatus(200, send("DELETE", "/api/admin/recommendations", "{\"ids\":[" + id + "]}", null, token), "delete");
        assertTrue(json.readTree(send("GET", "/api/admin/ai-assessments", null, null, token).body()).get("items").isEmpty());
    }

    private static HttpServer startFakeAi() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/recommend", exchange -> respond(exchange,
                    "{\"recommendedMenu\":\"AI 김치볶음밥\",\"confidence\":0.9,\"reasons\":[\"테스트\"],\"engine\":\"llm\"}"));
            server.createContext("/v1/assess", exchange -> {
                seenToken.set(exchange.getRequestHeaders().getFirst("X-AI-Token"));
                respond(exchange, "{\"riskScore\":0.9,\"riskLevel\":\"high\",\"flags\":[\"not_food\"],"
                        + "\"reasons\":[\"급식으로 제공할 수 없습니다.\"],\"engine\":\"llm\",\"extra\":1}");
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        exchange.getRequestBody().readAllBytes();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
