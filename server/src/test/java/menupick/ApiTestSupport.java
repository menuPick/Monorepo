package menupick;

import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 실제 HTTP로 서버를 띄워 검증합니다. 요청 제한 상태가 섞이지 않도록 테스트마다 새 컨텍스트를 씁니다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "menupick.admin.id=" + ApiTestSupport.ADMIN_ID,
        "menupick.admin.id-hash=",
        "menupick.db.url=",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
abstract class ApiTestSupport {
    static final String ADMIN_ID = "security-admin";

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        // 컨텍스트마다 새 폴더 → 새 H2 DB. 테스트끼리 데이터(월 1회 제한 등)가 섞이지 않습니다.
        registry.add("menupick.storage.dir", () -> storageDir.resolve(java.util.UUID.randomUUID().toString()).toString());
    }

    @LocalServerPort
    int port;

    final HttpClient client = HttpClient.newHttpClient();

    HttpResponse<String> send(String method, String path, String body) throws Exception {
        return send(method, path, body, null, null);
    }

    HttpResponse<String> send(String method, String path, String body, String origin, String bearer) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + path));
        if (origin != null) {
            builder.header("Origin", origin);
        }
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json; charset=utf-8")
                    .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    static void assertStatus(int expected, HttpResponse<String> response, String label) {
        assertEquals(expected, response.statusCode(), label + " — body: " + response.body());
    }
}
