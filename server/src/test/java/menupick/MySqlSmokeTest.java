package menupick;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 실제 MySQL/MariaDB 연결 확인. TEST_DB_PASSWORD가 있을 때만 실행됩니다.
 * 예) TEST_DB_URL=jdbc:mysql://localhost:3306/menupick_test?createDatabaseIfNotExist=true TEST_DB_PASSWORD=... ./mvnw test
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_PASSWORD", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "menupick.admin.id=test-admin", "menupick.admin.id-hash="
})
class MySqlSmokeTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("menupick.db.url", () -> env("TEST_DB_URL",
                "jdbc:mysql://localhost:3306/menupick_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&createDatabaseIfNotExist=true"));
        registry.add("menupick.db.user", () -> env("TEST_DB_USER", "root"));
        registry.add("menupick.db.password", () -> System.getenv("TEST_DB_PASSWORD"));
    }

    @LocalServerPort
    int port;

    @Test
    void healthAndInquiryRoundTrip() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> health = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/health")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertTrue(health.body().contains("\"storage\":\"database\""), health.body());

        HttpResponse<String> inquiry = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/inquiries"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"smoke\"}", StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(201, inquiry.statusCode(), inquiry.body());
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
