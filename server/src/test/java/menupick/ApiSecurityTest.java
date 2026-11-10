package menupick;

import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 기존 MenuPickServerSecurityTest를 Spring Boot 서버로 이식한 테스트. */
class ApiSecurityTest extends ApiTestSupport {

    @Test
    void protectsSensitiveAdminSurfacesAndRequestHandling() throws Exception {
        assertStatus(201, send("POST", "/api/inquiries", "{\"message\":\"private inquiry\"}"), "inquiry save");
        assertStatus(401, send("GET", "/api/inquiries/latest", null), "latest inquiry requires admin token");
        assertStatus(401, send("GET", "/api/admin/recommendations", null), "admin list requires token");
        assertStatus(401, send("GET", "/api/admin/inquiries", null, null, "forged-token"), "forged token rejected");

        for (int i = 0; i < 5; i++) {
            assertStatus(401, send("POST", "/api/admin/login", "{\"id\":\"wrong\"}"), "failed admin login " + i);
        }
        HttpResponse<String> locked = send("POST", "/api/admin/login", "{\"id\":\"" + ADMIN_ID + "\"}");
        assertStatus(429, locked, "admin login rate limit (even with correct id)");
        assertTrue(locked.body().contains("\"error\":\"rate_limited\""), locked.body());

        HttpResponse<String> blockedCors = send("OPTIONS", "/health", null, "https://evil.example", null);
        assertFalse(blockedCors.headers().firstValue("Access-Control-Allow-Origin").isPresent(),
                "unexpected CORS allowance for untrusted origin");

        HttpResponse<String> localCors = send("OPTIONS", "/health", null, "http://localhost:5173", null);
        assertEquals(204, localCors.statusCode());
        assertEquals("http://localhost:5173", localCors.headers().firstValue("Access-Control-Allow-Origin").orElse(""));

        String oversized = "{\"message\":\"" + "x".repeat(17 * 1024) + "\"}";
        HttpResponse<String> tooLarge = send("POST", "/api/inquiries", oversized);
        assertStatus(413, tooLarge, "oversized request body");
        assertTrue(tooLarge.body().contains("payload_too_large"), tooLarge.body());
    }

    @Test
    void limitsRecommendationsToOncePerNetworkPerMonth() throws Exception {
        String body = "{\"menuName\":\"비빔밥\",\"reason\":\"채소가 먹고 싶어요\"}";
        assertStatus(201, send("POST", "/api/recommendations", body), "first monthly recommendation");

        HttpResponse<String> second = send("POST", "/api/recommendations", body);
        assertStatus(429, second, "second monthly recommendation");
        assertTrue(second.body().contains("\"error\":\"monthly_limit_exceeded\""), second.body());
    }

    @Test
    void rateLimitsPublicInquiryWrites() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertStatus(201, send("POST", "/api/inquiries", "{\"message\":\"inquiry " + i + "\"}"), "allowed inquiry " + i);
        }
        HttpResponse<String> limited = send("POST", "/api/inquiries", "{\"message\":\"too many\"}");
        assertStatus(429, limited, "inquiry rate limit");
        assertEquals("600", limited.headers().firstValue("Retry-After").orElse(""), "retry after");
    }

    @Test
    void rejectsInvalidInputWithErrorJson() throws Exception {
        HttpResponse<String> missing = send("POST", "/api/inquiries", "{\"message\":\"   \"}");
        assertStatus(400, missing, "blank message");
        assertTrue(missing.body().contains("\"error\":\"bad_request\""), missing.body());

        assertStatus(400, send("POST", "/api/inquiries", "{not json"), "malformed json");
        assertStatus(404, send("GET", "/api/unknown", null), "unknown api");
        assertStatus(405, send("GET", "/api/admin/login", null), "login is POST only");
    }
}
