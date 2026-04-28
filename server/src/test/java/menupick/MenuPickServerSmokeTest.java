package menupick;

import menupick.json.JsonUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class MenuPickServerSmokeTest {
    private MenuPickServerSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        String dbUrl = envOrDefault("TEST_DB_URL", MenuPickServer.DEFAULT_DB_URL);
        String dbUser = envOrDefault("TEST_DB_USER", envOrDefault("DB_USER", "root"));
        String dbPassword = envOrDefault("TEST_DB_PASSWORD", envOrDefault("DB_PASSWORD", ""));

        if (dbPassword.isBlank()) {
            System.out.println("Smoke test skipped: TEST_DB_PASSWORD (or DB_PASSWORD) is not set.");
            return;
        }

        String adminId = envOrDefault("TEST_ADMIN_ID", "test-admin");
        String adminIdHash = MenuPickServer.sha256Hex(adminId);

        MenuPickServer server = new MenuPickServer(0, dbUrl, dbUser, dbPassword, adminIdHash);
        server.start();

        try {
            HttpClient client = HttpClient.newHttpClient();
            int port = server.getPort();

            HttpResponse<String> recommendationResponse = client.send(
                    request(port, "/api/recommendations",
                            "{\"menuName\":\"\",\"reason\":\"매운 게 먹고 싶어요\"}"),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            assertStatus(201, recommendationResponse.statusCode(), "recommendation save");
            Map<String, String> recommendation = JsonUtil.parseFlatObject(recommendationResponse.body());
            assertEquals("떡볶이", recommendation.get("recommendedMenu"), "recommended menu");

            HttpResponse<String> latestRecommendation = client.send(
                    request(port, "/api/recommendations/latest", null),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            assertStatus(200, latestRecommendation.statusCode(), "latest recommendation");
            Map<String, String> latestRecommendationBody = JsonUtil.parseFlatObject(latestRecommendation.body());
            assertEquals("떡볶이", latestRecommendationBody.get("recommendedMenu"), "latest recommendation body");

            HttpResponse<String> inquiryResponse = client.send(
                    request(port, "/api/inquiries", "{\"message\":\"문의 내용을 남깁니다\"}"),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            assertStatus(201, inquiryResponse.statusCode(), "inquiry save");
            Map<String, String> inquiry = JsonUtil.parseFlatObject(inquiryResponse.body());
            assertEquals(MenuPickServer.ADMIN_EMAIL, inquiry.get("adminEmail"), "admin email");

            HttpResponse<String> adminLogin = client.send(
                    request(port, "/api/admin/login",
                            "{\"id\":\"" + adminId + "\"}"),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            assertStatus(200, adminLogin.statusCode(), "admin login");
            Map<String, String> adminLoginBody = JsonUtil.parseFlatObject(adminLogin.body());
            String token = adminLoginBody.get("token");
            if (token == null || token.isBlank()) {
                throw new AssertionError("admin token is missing");
            }

            System.out.println("Smoke test passed.");
        } finally {
            server.stop(0);
        }
    }

    private static HttpRequest request(int port, String path, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path));
        if (body == null) {
            return builder.GET().build();
        }
        return builder
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    private static void assertStatus(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + " expected status " + expected + " but was " + actual);
        }
    }

    private static void assertEquals(String expected, String actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " expected '" + expected + "' but was '" + actual + "'");
        }
    }

    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}

