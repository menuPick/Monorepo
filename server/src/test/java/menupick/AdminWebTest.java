package menupick;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 관리자 Flutter Web(build/admin_web)을 /admin/ 에서 제공하는지, 성능용 헤더·압축이 붙는지 확인합니다. */
class AdminWebTest extends ApiTestSupport {
    static final Path ADMIN_WEB = createAdminWeb();

    @DynamicPropertySource
    static void adminWeb(DynamicPropertyRegistry registry) {
        registry.add("menupick.admin-web-dir", ADMIN_WEB::toString);
    }

    @Test
    void servesAdminWebWithIsolationHeaders() throws Exception {
        HttpResponse<String> redirect = send("GET", "/admin", null);
        assertEquals(302, redirect.statusCode(), "/admin redirects");
        assertTrue(redirect.headers().firstValue("Location").orElse("").endsWith("/admin/"));

        HttpResponse<String> index = send("GET", "/admin/", null);
        assertStatus(200, index, "index");
        assertTrue(index.body().contains("<base href=\"/admin/\">"), index.body());
        assertEquals("same-origin", index.headers().firstValue("Cross-Origin-Opener-Policy").orElse(""));
        assertEquals("require-corp", index.headers().firstValue("Cross-Origin-Embedder-Policy").orElse(""));
        assertEquals("no-cache", index.headers().firstValue("Cache-Control").orElse(""));

        HttpResponse<String> wasm = send("GET", "/admin/main.dart.wasm", null);
        assertStatus(200, wasm, "wasm");
        assertEquals("application/wasm", wasm.headers().firstValue("Content-Type").orElse(""));

        assertStatus(404, send("GET", "/admin/missing.js", null), "missing file");
    }

    @Test
    void compressesLargeScriptsWhenAskedForGzip() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/admin/main.dart.js"))
                .header("Accept-Encoding", "gzip").build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode());
        assertEquals("gzip", response.headers().firstValue("Content-Encoding").orElse(""));
        assertTrue(response.body().length < 20_000, "compressed size " + response.body().length);
    }

    @Test
    void isolationHeadersOnlyOnAdminWeb() throws Exception {
        HttpResponse<String> health = send("GET", "/health", null);
        assertStatus(200, health, "health");
        assertFalse(health.headers().firstValue("Cross-Origin-Embedder-Policy").isPresent(),
                "API responses must not get COEP");
    }

    private static Path createAdminWeb() {
        try {
            Path dir = Files.createTempDirectory("admin-web-test");
            Files.writeString(dir.resolve("index.html"),
                    "<!DOCTYPE html><html><head><base href=\"/admin/\"></head><body>admin</body></html>",
                    StandardCharsets.UTF_8);
            // 압축 확인용으로 반복이 많은 큰 JS 파일
            Files.writeString(dir.resolve("main.dart.js"), "console.log('menupick');\n".repeat(20_000),
                    StandardCharsets.UTF_8);
            Files.write(dir.resolve("main.dart.wasm"), new byte[]{0, 'a', 's', 'm', 1, 0, 0, 0});
            dir.toFile().deleteOnExit();
            return dir;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
