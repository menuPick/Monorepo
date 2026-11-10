package menupick.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import menupick.service.AdminAuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

/** 관리자 API 앞단에서 Bearer 토큰을 검사합니다. */
public class AdminAuthInterceptor implements HandlerInterceptor {
    private final AdminAuthService adminAuthService;

    public AdminAuthInterceptor(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!adminAuthService.isValidBearer(request.getHeader("Authorization"))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "관리자 인증이 필요합니다.");
        }
        return true;
    }
}
