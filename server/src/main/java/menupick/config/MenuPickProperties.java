package menupick.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 서버 설정. 기본값과 환경변수 매핑은 application.yml에 있습니다.
 */
@ConfigurationProperties("menupick")
public record MenuPickProperties(
        String apiVersion,
        String adminEmail,
        int maxBodyBytes,
        boolean trustProxyHeaders,
        String allowedOrigins,
        Admin admin,
        RateLimit rateLimit,
        Storage storage,
        Db db,
        Ai ai,
        String adminWebDir
) {
    /** Python AI 서버(ai/). baseUrl이 비어 있으면 AI 없이 규칙 엔진만 씁니다. */
    public record Ai(String baseUrl, String token, Duration timeout) {
        public boolean enabled() {
            return baseUrl != null && !baseUrl.isBlank();
        }
    }

    public record Admin(String id, String idHash, String idHashFile, Duration sessionTtl,
                        int loginFailLimit, Duration loginFailWindow) {
    }

    /** monthlyRecommendation=false면 네트워크당 월 1회 추천 제한을 끕니다(로컬 개발용). */
    public record RateLimit(Duration window, int recommendations, int inquiries, boolean monthlyRecommendation) {
    }

    public record Storage(String dir) {
    }

    public record Db(String url, String user, String password, int maxPoolSize) {
    }

    public Set<String> allowedOriginSet() {
        Set<String> origins = new LinkedHashSet<>();
        if (allowedOrigins != null) {
            Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .filter(origin -> !origin.isEmpty())
                    .forEach(origins::add);
        }
        return Set.copyOf(origins);
    }
}
