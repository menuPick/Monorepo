package menupick.ai;

import com.sun.net.httpserver.HttpServer;
import menupick.config.MenuPickProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AI 서버가 이상한 응답을 주면 예외 없이 empty(→ 규칙 엔진 대체)가 되는지 확인합니다. */
class AiClientTest {
    private HttpServer server;
    private final AtomicReference<String> seenToken = new AtomicReference<>();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private AiClient clientFor(int status, String body, String token, Duration delay) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            seenToken.set(exchange.getRequestHeaders().getFirst("X-AI-Token"));
            exchange.getRequestBody().readAllBytes();
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        return new AiClient(properties(new MenuPickProperties.Ai(url, token, Duration.ofSeconds(1))),
                JsonMapper.builder().build());
    }

    private static MenuPickProperties properties(MenuPickProperties.Ai ai) {
        return new MenuPickProperties("test", "admin@local", 16384, false, "", null, null, null, null, ai, null);
    }

    @Test
    void parsesValidResponsesAndIgnoresUnknownFields() throws Exception {
        AiClient client = clientFor(200,
                "{\"recommendedMenu\":\"제육덮밥\",\"confidence\":0.7,\"reasons\":[\"고기\"],\"engine\":\"llm\",\"extra\":true}",
                "secret", Duration.ZERO);
        AiClient.RecommendResult result = client.recommend("고기", "든든하게").orElseThrow();
        assertEquals("제육덮밥", result.recommendedMenu());
        assertEquals("secret", seenToken.get(), "token header sent");
    }

    @Test
    void returnsEmptyOnServerError() throws Exception {
        assertTrue(clientFor(500, "{\"error\":\"boom\"}", "", Duration.ZERO).assess("a", "b", "c").isEmpty());
        assertNull(seenToken.get(), "no token header when token is blank");
    }

    @Test
    void returnsEmptyOnInvalidJson() throws Exception {
        assertTrue(clientFor(200, "not json", "", Duration.ZERO).recommend("a", "b").isEmpty());
    }

    @Test
    void returnsEmptyOnBlankMenu() throws Exception {
        assertTrue(clientFor(200, "{\"recommendedMenu\":\"  \",\"confidence\":1,\"reasons\":[],\"engine\":\"llm\"}",
                "", Duration.ZERO).recommend("a", "b").isEmpty());
    }

    @Test
    void returnsEmptyOnTimeout() throws Exception {
        AiClient client = clientFor(200, "{}", "", Duration.ofSeconds(3));
        long start = System.nanoTime();
        assertTrue(client.assess("a", "b", "c").isEmpty());
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toSeconds() < 3, "gave up at the 1s timeout");
    }

    @Test
    void disabledWhenBaseUrlIsBlank() {
        AiClient client = new AiClient(properties(new MenuPickProperties.Ai("", "", null)), JsonMapper.builder().build());
        assertFalse(client.enabled());
        assertTrue(client.recommend("a", "b").isEmpty());
    }
}
