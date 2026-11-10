package menupick.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import menupick.ai.AiClient;
import menupick.config.MenuPickProperties;
import menupick.domain.Inquiry;
import menupick.domain.Recommendation;
import menupick.service.AiAssessmentService;
import menupick.service.FixedWindowRateLimiter;
import menupick.service.InquiryService;
import menupick.service.RecommendationService;
import menupick.web.dto.InquiryRequest;
import menupick.web.dto.ReasonDraftResponse;
import menupick.web.dto.RecommendationRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 사용자 앱(lib/main.dart)이 쓰는 공개 API. */
@Tag(name = "사용자 API")
@RestController
public class PublicApiController {
    private static final String RATE_LIMITED_MESSAGE = "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.";
    /** 이유 자세히 쓰기는 저장하지 않으므로 추천 제출보다 넉넉하게(10분에 30번). */
    private static final int REASON_DRAFT_LIMIT = 30;

    private final RecommendationService recommendations;
    private final InquiryService inquiries;
    private final AiAssessmentService aiAssessments;
    private final AiClient ai;
    private final ClientKeyResolver clientKeys;
    private final FixedWindowRateLimiter publicWriteLimiter;
    private final int recommendationLimit;
    private final int inquiryLimit;

    public PublicApiController(RecommendationService recommendations, InquiryService inquiries,
                               AiAssessmentService aiAssessments, AiClient ai, ClientKeyResolver clientKeys,
                               FixedWindowRateLimiter publicWriteLimiter, MenuPickProperties properties) {
        this.recommendations = recommendations;
        this.aiAssessments = aiAssessments;
        this.ai = ai;
        this.inquiries = inquiries;
        this.clientKeys = clientKeys;
        this.publicWriteLimiter = publicWriteLimiter;
        this.recommendationLimit = properties.rateLimit().recommendations();
        this.inquiryLimit = properties.rateLimit().inquiries();
    }

    @Operation(summary = "메뉴 추천 제출 (네트워크당 월 1회)")
    @PostMapping("/api/recommendations")
    @ResponseStatus(HttpStatus.CREATED)
    public Recommendation submitRecommendation(@RequestBody RecommendationRequest body, HttpServletRequest request) {
        String clientKey = clientKeys.resolve(request);
        requireWriteQuota("recommendations", clientKey, recommendationLimit);

        String menuName = Inputs.required(body.menuName(), "menuName", 255);
        String reason = Inputs.required(body.reason(), "reason", 2000);
        String recommendedMenu = Inputs.optional(body.recommendedMenu(), 255);
        String category = Inputs.optional(body.category(), 64);

        Recommendation saved = recommendations.submit(clientKey, menuName, reason, recommendedMenu, category)
                .orElseThrow(() -> new ApiException(HttpStatus.TOO_MANY_REQUESTS, "monthly_limit_exceeded",
                        "메뉴 추천은 한 달에 한 번만 올릴 수 있습니다."));
        aiAssessments.assessAsync(saved);
        return saved;
    }

    @Operation(summary = "먹고 싶은 이유를 AI가 자세히 (사용자가 쓴 말 뒤에 문장을 덧붙임, 저장하지 않음)")
    @PostMapping("/api/recommendations/reason-draft")
    public ReasonDraftResponse draftReason(@RequestBody RecommendationRequest body, HttpServletRequest request) {
        requireWriteQuota("reason-draft", clientKeys.resolve(request), REASON_DRAFT_LIMIT);
        String menuName = Inputs.optional(body.menuName(), 255);
        String reason = Inputs.optional(body.reason(), 2000);
        return ai.elaborate(menuName, reason)
                .map(result -> new ReasonDraftResponse(result.reason(), result.added(), result.engine()))
                .orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ai_unavailable",
                        "AI 서버에 연결할 수 없어 이유를 만들지 못했습니다."));
    }

    @Operation(summary = "공개된 결정 메뉴 조회")
    @GetMapping("/api/recommendations/latest")
    public Recommendation latestRecommendation() {
        return recommendations.latestPublished()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_published", "결정 메뉴가 아직 공개되지 않았습니다."));
    }

    @Operation(summary = "문의 제출")
    @PostMapping("/api/inquiries")
    @ResponseStatus(HttpStatus.CREATED)
    public Inquiry submitInquiry(@RequestBody InquiryRequest body, HttpServletRequest request) {
        requireWriteQuota("inquiries", clientKeys.resolve(request), inquiryLimit);
        return inquiries.submit(Inputs.required(body.message(), "message", 4000));
    }

    private void requireWriteQuota(String endpoint, String clientKey, int limit) {
        if (!publicWriteLimiter.tryAcquire(endpoint + ':' + clientKey, limit)) {
            throw ApiException.rateLimited(RATE_LIMITED_MESSAGE, publicWriteLimiter.window());
        }
    }
}
