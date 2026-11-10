package menupick.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import menupick.service.AdminAuthService;
import menupick.service.FixedWindowRateLimiter;
import menupick.web.AdminAuthInterceptor;
import menupick.web.AdminWebHeadersFilter;
import menupick.web.BodySizeLimitFilter;
import menupick.web.CorsFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {
    private final AdminAuthService adminAuthService;
    private final MenuPickProperties properties;

    public WebConfig(AdminAuthService adminAuthService, MenuPickProperties properties) {
        this.adminAuthService = adminAuthService;
        this.properties = properties;
    }

    /** 관리자 Flutter Web(빌드 결과)을 /admin/ 에서 제공합니다. 폴더가 없으면 404. */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String dir = properties.adminWebDir();
        if (dir == null || dir.isBlank()) {
            return;
        }
        Path path = Path.of(dir).toAbsolutePath().normalize();
        registry.addResourceHandler("/admin/**").addResourceLocations(path.toUri().toString());
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/admin", "/admin/");
        registry.addViewController("/admin/").setViewName("forward:/admin/index.html");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminAuthInterceptor(adminAuthService))
                .addPathPatterns("/api/admin/**", "/api/inquiries/latest")
                .excludePathPatterns("/api/admin/login");
    }

    @Bean
    FilterRegistrationBean<AdminWebHeadersFilter> adminWebHeadersFilter() {
        FilterRegistrationBean<AdminWebHeadersFilter> registration =
                new FilterRegistrationBean<>(new AdminWebHeadersFilter());
        registration.addUrlPatterns("/admin", "/admin/*");
        return registration;
    }

    @Bean
    FilterRegistrationBean<CorsFilter> corsFilter(MenuPickProperties properties) {
        FilterRegistrationBean<CorsFilter> registration =
                new FilterRegistrationBean<>(new CorsFilter(properties.allowedOriginSet()));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    FilterRegistrationBean<BodySizeLimitFilter> bodySizeLimitFilter(MenuPickProperties properties, ObjectMapper json) {
        FilterRegistrationBean<BodySizeLimitFilter> registration =
                new FilterRegistrationBean<>(new BodySizeLimitFilter(properties.maxBodyBytes(), json));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    @Bean
    static Clock clock() {
        return Clock.systemUTC();
    }

    /** 공개 쓰기 API(추천·문의) 요청 제한. */
    @Bean
    FixedWindowRateLimiter publicWriteLimiter(MenuPickProperties properties, Clock clock) {
        return new FixedWindowRateLimiter(properties.rateLimit().window(), clock);
    }

    @Bean
    RateLimiterCleanup rateLimiterCleanup(FixedWindowRateLimiter publicWriteLimiter) {
        return new RateLimiterCleanup(publicWriteLimiter);
    }

    @Bean
    OpenAPI menuPickOpenApi(MenuPickProperties properties) {
        return new OpenAPI()
                .info(new Info().title("MenuPick API").version(properties.apiVersion())
                        .description("관리자 API는 /api/admin/login으로 받은 token을 Authorize에 입력하세요."))
                .components(new Components().addSecuritySchemes("admin",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")));
    }

    /** 만료된 요청 제한 기록을 주기적으로 지워 메모리 사용량을 일정하게 유지합니다. */
    static final class RateLimiterCleanup {
        private final FixedWindowRateLimiter limiter;

        RateLimiterCleanup(FixedWindowRateLimiter limiter) {
            this.limiter = limiter;
        }

        @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT5M")
        void evict() {
            limiter.evictExpired();
        }
    }
}
