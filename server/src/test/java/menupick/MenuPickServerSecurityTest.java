package menupick;

import menupick.db.DatabaseRepository;
import menupick.model.InquiryRecord;
import menupick.model.MenuDecisionSetting;
import menupick.model.RecommendationRecord;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public final class MenuPickServerSecurityTest {
    @Test
    void protectsSensitiveAdminSurfacesAndRequestHandling() throws Exception {
        String adminId = "security-admin";
        MenuPickServer server = new MenuPickServer(0, new InMemoryDatabase(), MenuPickServer.sha256Hex(adminId));
        server.start();

        try {
            HttpClient client = HttpClient.newHttpClient();
            int port = server.getPort();

            assertStatus(201, client.send(
                    request(port, "/api/inquiries", "POST", "{\"message\":\"private inquiry\"}", null),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            ).statusCode(), "inquiry save");

            assertStatus(401, client.send(
                    request(port, "/api/inquiries/latest", "GET", null, null),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            ).statusCode(), "latest inquiry requires admin token");

            for (int i = 0; i < 5; i++) {
                assertStatus(401, client.send(
                        request(port, "/api/admin/login", "POST", "{\"id\":\"wrong\"}", null),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                ).statusCode(), "failed admin login " + i);
            }
            assertStatus(429, client.send(
                    request(port, "/api/admin/login", "POST", "{\"id\":\"wrong\"}", null),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            ).statusCode(), "admin login rate limit");

            HttpResponse<String> blockedCors = client.send(
                    request(port, "/health", "OPTIONS", null, "https://evil.example"),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            assertFalse(blockedCors.headers().firstValue("Access-Control-Allow-Origin").isPresent(),
                    "unexpected CORS allowance for untrusted origin");

            HttpResponse<String> localCors = client.send(
                    request(port, "/health", "OPTIONS", null, "http://localhost:5173"),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            assertEquals("http://localhost:5173",
                    localCors.headers().firstValue("Access-Control-Allow-Origin").orElse(""),
                    "localhost CORS origin");

            String oversized = "{\"message\":\"" + "x".repeat(17 * 1024) + "\"}";
            assertStatus(413, client.send(
                    request(port, "/api/inquiries", "POST", oversized, null),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            ).statusCode(), "oversized request body");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void limitsRecommendationsToOncePerNetworkPerMonthEvenWhenClientIdChanges() throws Exception {
        MenuPickServer server = new MenuPickServer(0, new InMemoryDatabase(), MenuPickServer.sha256Hex("security-admin"));
        server.start();

        try {
            HttpClient client = HttpClient.newHttpClient();
            int port = server.getPort();
            String body = "{\"menuName\":\"비빔밥\",\"reason\":\"채소가 먹고 싶어요\"}";

            assertStatus(201, client.send(
                    request(port, "/api/recommendations", "POST", body, null, "monthly-user"),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            ).statusCode(), "first monthly recommendation");

            assertStatus(429, client.send(
                    request(port, "/api/recommendations", "POST", body, null, "monthly-user"),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            ).statusCode(), "second monthly recommendation");

            assertStatus(429, client.send(
                    request(port, "/api/recommendations", "POST", body, null, "other-monthly-user"),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            ).statusCode(), "rotated client id monthly recommendation");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rateLimitsPublicInquiryWrites() throws Exception {
        MenuPickServer server = new MenuPickServer(0, new InMemoryDatabase(), MenuPickServer.sha256Hex("security-admin"));
        server.start();

        try {
            HttpClient client = HttpClient.newHttpClient();
            int port = server.getPort();
            for (int i = 0; i < 5; i++) {
                assertStatus(201, client.send(
                        request(port, "/api/inquiries", "POST", "{\"message\":\"inquiry " + i + "\"}", null),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                ).statusCode(), "allowed inquiry " + i);
            }
            HttpResponse<String> limited = client.send(
                    request(port, "/api/inquiries", "POST", "{\"message\":\"too many\"}", null),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            assertStatus(429, limited.statusCode(), "inquiry rate limit");
            assertEquals("600", limited.headers().firstValue("Retry-After").orElse(""), "retry after");
        } finally {
            server.stop(0);
        }
    }

    private static HttpRequest request(int port, String path, String method, String body, String origin) {
        return request(port, path, method, body, origin, null);
    }

    private static HttpRequest request(int port, String path, String method, String body, String origin, String userId) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path));
        if (origin != null) {
            builder.header("Origin", origin);
        }
        if (userId != null) {
            builder.header("X-MenuPick-User-Id", userId);
        }
        if (body == null) {
            return builder.method(method, HttpRequest.BodyPublishers.noBody()).build();
        }
        return builder
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    private static void assertStatus(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + " expected status " + expected + " but was " + actual);
        }
    }

    private static final class InMemoryDatabase implements DatabaseRepository {
        private final List<InquiryRecord> inquiries = new ArrayList<>();
        private final List<RecommendationRecord> recommendations = new ArrayList<>();
        private final Set<String> monthlyRecommendations = new HashSet<>();

        @Override
        public synchronized RecommendationRecord saveMonthlyRecommendation(
                String userKey,
                String monthKey,
                String menuName,
                String reason,
                String recommendedMenu
        ) {
            if (!monthlyRecommendations.add(userKey + ":" + monthKey)) {
                return null;
            }
            return saveRecommendation(menuName, reason, recommendedMenu);
        }

        @Override
        public synchronized RecommendationRecord saveRecommendation(String menuName, String reason, String recommendedMenu) {
            RecommendationRecord record = new RecommendationRecord(recommendations.size() + 1L, menuName, reason, recommendedMenu, Instant.now().toString());
            recommendations.add(record);
            return record;
        }

        @Override
        public synchronized RecommendationRecord getLatestRecommendation() {
            return recommendations.isEmpty() ? null : recommendations.get(recommendations.size() - 1);
        }

        @Override
        public synchronized RecommendationRecord getRecommendationById(long id) {
            return recommendations.stream().filter(record -> record.id() == id).findFirst().orElse(null);
        }

        @Override
        public MenuDecisionSetting getMenuDecisionSetting() {
            return null;
        }

        @Override
        public void upsertMenuDecisionSetting(long recommendationId, String publishAt) {
        }

        @Override
        public synchronized InquiryRecord saveInquiry(String message, String adminEmail) {
            InquiryRecord record = new InquiryRecord(inquiries.size() + 1L, message, adminEmail, Instant.now().toString());
            inquiries.add(record);
            return record;
        }

        @Override
        public synchronized InquiryRecord getLatestInquiry() {
            return inquiries.isEmpty() ? null : inquiries.get(inquiries.size() - 1);
        }

        @Override
        public synchronized List<RecommendationRecord> listRecommendations(int limit) {
            return List.copyOf(recommendations);
        }

        @Override
        public synchronized List<InquiryRecord> listInquiries(int limit) {
            return List.copyOf(inquiries);
        }

        @Override
        public int deleteRecommendations(List<Long> ids) {
            int before = recommendations.size();
            recommendations.removeIf(record -> ids.contains(record.id()));
            return before - recommendations.size();
        }

        @Override
        public int deleteInquiries(List<Long> ids) {
            int before = inquiries.size();
            inquiries.removeIf(record -> ids.contains(record.id()));
            return before - inquiries.size();
        }

        @Override
        public void close() throws IOException {
        }
    }
}
