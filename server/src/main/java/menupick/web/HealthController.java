package menupick.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import menupick.config.MenuPickProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "상태")
@RestController
public class HealthController {
    private final Map<String, String> body;

    public HealthController(MenuPickProperties properties) {
        String url = properties.db().url();
        String storage = url == null || url.isBlank() ? "h2" : "database";
        this.body = Map.of("status", "ok", "version", properties.apiVersion(), "storage", storage);
    }

    @Operation(summary = "서버 상태")
    @GetMapping("/health")
    public Map<String, String> health() {
        return body;
    }
}
