package menupick.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import menupick.config.MenuPickProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Python AI 서버(ai/) 호출. 실패하면 empty를 돌려 호출부가 규칙 엔진으로 대체합니다.
 * 계약은 ai/app/schemas.py와 docs/ai-design.md에 있습니다.
 */
@Component
public class AiClient {
    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    private final MenuPickProperties.Ai config;
    private final ObjectMapper json;
    private final HttpClient http;
    private final Duration timeout;

    public AiClient(MenuPickProperties properties, ObjectMapper json) {
        this.config = properties.ai() == null ? new MenuPickProperties.Ai("", "", null) : properties.ai();
        this.json = json;
        this.timeout = config.timeout() == null ? Duration.ofSeconds(90) : config.timeout();
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    public boolean enabled() {
        return config.enabled();
    }

    public Optional<RecommendResult> recommend(String menuName, String reason) {
        return post("/v1/recommend", Map.of("menuName", menuName, "reason", reason), RecommendResult.class)
                .filter(result -> result.recommendedMenu() != null && !result.recommendedMenu().isBlank());
    }

    public Optional<AssessResult> assess(String menuName, String reason, String recommendedMenu) {
        return post("/v1/assess",
                Map.of("menuName", menuName, "reason", reason, "recommendedMenu", recommendedMenu),
                AssessResult.class);
    }

    private <T> Optional<T> post(String path, Map<String, String> body, Class<T> type) {
        if (!enabled()) {
            return Optional.empty();
        }
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl().replaceAll("/+$", "") + path))
                    .timeout(timeout)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8));
            if (config.token() != null && !config.token().isBlank()) {
                request.header("X-AI-Token", config.token());
            }
            HttpResponse<String> response = http.send(request.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.warn("AI server {} returned status={}", path, response.statusCode());
                return Optional.empty();
            }
            return Optional.of(json.readValue(response.body(), type));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("AI server {} unavailable: {}", path, ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RecommendResult(String recommendedMenu, double confidence, List<String> reasons, String engine) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AssessResult(double riskScore, String riskLevel, List<String> flags, List<String> reasons,
                               String engine) {
    }
}
