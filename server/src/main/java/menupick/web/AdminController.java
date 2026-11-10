package menupick.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import menupick.config.MenuPickProperties;
import menupick.domain.AiAssessment;
import menupick.domain.Inquiry;
import menupick.domain.Recommendation;
import menupick.service.AdminAuthService;
import menupick.service.AiAssessmentService;
import menupick.service.AdminAuthService.LoginResult;
import menupick.service.InquiryService;
import menupick.service.RecommendationService;
import menupick.web.dto.AiAssessmentsResponse;
import menupick.web.dto.DecisionRequest;
import menupick.web.dto.DeletedResponse;
import menupick.web.dto.ItemsResponse;
import menupick.web.dto.LoginRequest;
import menupick.web.dto.LoginResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/** 관리자 앱(lib/admin_main.dart)이 쓰는 API. 로그인 외에는 {@link AdminAuthInterceptor}가 토큰을 검사합니다. */
@Tag(name = "관리자 API")
@RestController
public class AdminController {
    private final AdminAuthService auth;
    private final RecommendationService recommendations;
    private final InquiryService inquiries;
    private final AiAssessmentService aiAssessments;
    private final ClientKeyResolver clientKeys;
    private final ObjectMapper json;
    private final String adminEmail;

    public AdminController(AdminAuthService auth, RecommendationService recommendations, InquiryService inquiries,
                           AiAssessmentService aiAssessments, ClientKeyResolver clientKeys, ObjectMapper json,
                           MenuPickProperties properties) {
        this.auth = auth;
        this.recommendations = recommendations;
        this.inquiries = inquiries;
        this.aiAssessments = aiAssessments;
        this.clientKeys = clientKeys;
        this.json = json;
        this.adminEmail = properties.adminEmail();
    }

    @Operation(summary = "관리자 로그인 (5분에 5회 실패 시 잠금)")
    @PostMapping("/api/admin/login")
    public LoginResponse login(@RequestBody LoginRequest body, HttpServletRequest request) {
        LoginResult result = auth.login(clientKeys.resolve(request), body.id());
        return switch (result.status()) {
            case RATE_LIMITED -> throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited",
                    "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해주세요.");
            case UNAUTHORIZED -> throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized",
                    "관리자 ID가 올바르지 않습니다.");
            case SUCCESS -> new LoginResponse(result.token(), adminEmail, result.expiresAt().toString());
        };
    }

    @Operation(summary = "추천 목록 (최신순)")
    @SecurityRequirement(name = "admin")
    @GetMapping("/api/admin/recommendations")
    public ItemsResponse<Recommendation> listRecommendations(@RequestParam(required = false) String limit) {
        return new ItemsResponse<>(recommendations.recent(Inputs.limit(limit)));
    }

    @Operation(summary = "추천 삭제 — body: {\"ids\":[1,2,3]}")
    @SecurityRequirement(name = "admin")
    @RequestMapping(path = "/api/admin/recommendations", method = {RequestMethod.POST, RequestMethod.DELETE})
    public DeletedResponse deleteRecommendations(@RequestBody(required = false) String body) {
        return new DeletedResponse(recommendations.delete(IdsParser.parse(body, json)));
    }

    @Operation(summary = "최근 추천의 AI 위험 판별 결과 (점수·근거만, 조치는 관리자가 결정)")
    @SecurityRequirement(name = "admin")
    @GetMapping("/api/admin/ai-assessments")
    public AiAssessmentsResponse listAiAssessments(@RequestParam(required = false) String limit) {
        List<Long> ids = recommendations.recent(Inputs.limit(limit)).stream().map(Recommendation::id).toList();
        return new AiAssessmentsResponse(aiAssessments.enabled(), aiAssessments.findFor(ids));
    }

    @Operation(summary = "추천 항목 AI 위험 판별 다시 실행")
    @SecurityRequirement(name = "admin")
    @PostMapping("/api/admin/recommendations/{id}/ai-assessment")
    public AiAssessment reassess(@PathVariable long id) {
        if (!aiAssessments.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ai_disabled", "AI 서버가 설정되지 않았습니다.");
        }
        try {
            return aiAssessments.reassess(id).orElseThrow(() -> ApiException.notFound("추천 항목을 찾을 수 없습니다."));
        } catch (AiAssessmentService.AiUnavailableException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "ai_unavailable", "AI 서버에 연결할 수 없습니다.");
        }
    }

    @Operation(summary = "문의 목록 (최신순)")
    @SecurityRequirement(name = "admin")
    @GetMapping("/api/admin/inquiries")
    public ItemsResponse<Inquiry> listInquiries(@RequestParam(required = false) String limit) {
        return new ItemsResponse<>(inquiries.recent(Inputs.limit(limit)));
    }

    @Operation(summary = "문의 삭제 — body: {\"ids\":[1,2,3]}")
    @SecurityRequirement(name = "admin")
    @RequestMapping(path = "/api/admin/inquiries", method = {RequestMethod.POST, RequestMethod.DELETE})
    public DeletedResponse deleteInquiries(@RequestBody(required = false) String body) {
        return new DeletedResponse(inquiries.delete(IdsParser.parse(body, json)));
    }

    @Operation(summary = "가장 최근 문의")
    @SecurityRequirement(name = "admin")
    @GetMapping("/api/inquiries/latest")
    public Inquiry latestInquiry() {
        return inquiries.latest().orElseThrow(() -> ApiException.notFound("문의가 없습니다."));
    }

    @Operation(summary = "결정 메뉴와 공개 시각 지정")
    @SecurityRequirement(name = "admin")
    @PostMapping("/api/admin/decision")
    public Map<String, Boolean> decide(@RequestBody DecisionRequest body) {
        Long recommendationId = body.recommendationId();
        if (recommendationId == null || recommendationId <= 0) {
            throw ApiException.badRequest("recommendationId 값이 올바르지 않습니다.");
        }
        String publishAtText = Inputs.required(body.publishAt(), "publishAt", 64);
        Instant publishAt;
        try {
            publishAt = Instant.parse(publishAtText);
        } catch (DateTimeParseException ex) {
            throw ApiException.badRequest("publishAt 값이 올바르지 않습니다.");
        }
        if (!recommendations.decide(recommendationId, publishAt)) {
            throw ApiException.notFound("추천 항목을 찾을 수 없습니다.");
        }
        return Map.of("ok", true);
    }
}
