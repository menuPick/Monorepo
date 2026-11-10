package menupick.web;

import jakarta.servlet.http.HttpServletRequest;
import menupick.config.MenuPickProperties;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 요청 제한/월 1회 제한에 쓰는 클라이언트 키(IP).
 * TRUST_PROXY_HEADERS=true일 때만 X-Forwarded-For 첫 값을 신뢰합니다(앱이 보내는 사용자 ID 헤더는 신뢰하지 않음).
 */
@Component
public class ClientKeyResolver {
    private static final Pattern IP_LIKE = Pattern.compile("[0-9a-fA-F:.]{1,64}");

    private final boolean trustProxyHeaders;

    public ClientKeyResolver(MenuPickProperties properties) {
        this.trustProxyHeaders = properties.trustProxyHeaders();
    }

    public String resolve(HttpServletRequest request) {
        if (trustProxyHeaders) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null) {
                int comma = forwardedFor.indexOf(',');
                String candidate = (comma < 0 ? forwardedFor : forwardedFor.substring(0, comma)).trim();
                if (IP_LIKE.matcher(candidate).matches()) {
                    return candidate;
                }
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }
}
