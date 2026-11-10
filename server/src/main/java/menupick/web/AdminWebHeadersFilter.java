package menupick.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 관리자 Flutter Web(/admin/) 성능용 응답 헤더.
 * - COOP/COEP: WebAssembly 빌드(skwasm)가 여러 스레드로 렌더링할 수 있게 합니다(cross-origin isolation).
 *   외부 리소스는 쓰지 않도록 --no-web-resources-cdn으로 빌드합니다.
 * - Cache-Control no-cache: 매번 바뀌었는지만 확인(304)하므로 다시 빌드해도 옛 화면이 남지 않습니다.
 */
public class AdminWebHeadersFilter extends OncePerRequestFilter {
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !(path.equals("/admin") || path.startsWith("/admin/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        response.setHeader("Cross-Origin-Embedder-Policy", "require-corp");
        response.setHeader("Cache-Control", "no-cache");
        chain.doFilter(request, response);
    }
}
