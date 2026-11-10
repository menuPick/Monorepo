package menupick;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 로컬 실행(server/run_local.*)과 같은 설정: 관리자 ID "test"로 로그인됩니다. */
@TestPropertySource(properties = "menupick.admin.id=test")
class LocalAdminIdTest extends ApiTestSupport {

    @Test
    void localAdminIdTestCanLogIn() throws Exception {
        var ok = send("POST", "/api/admin/login", "{\"id\":\"test\"}");
        assertStatus(200, ok, "login with test");
        assertTrue(ok.body().contains("\"token\""), ok.body());

        assertStatus(401, send("POST", "/api/admin/login", "{\"id\":\"TEST\"}"), "id is case-sensitive");
        assertStatus(401, send("POST", "/api/admin/login", "{\"id\":\"\"}"), "blank id rejected");
    }
}
