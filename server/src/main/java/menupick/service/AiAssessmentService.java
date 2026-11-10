package menupick.service;

import jakarta.annotation.PreDestroy;
import menupick.ai.AiClient;
import menupick.domain.AiAssessment;
import menupick.domain.Recommendation;
import menupick.repository.MenuPickRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 추천 항목의 위험 판별을 AI 서버에 맡기고 결과(점수·근거)만 저장합니다.
 * 같은 항목을 다시 판별하면 덮어쓰므로 중복 실행·재시작 후 재실행에 안전합니다.
 */
@Service
public class AiAssessmentService {
    private static final Logger log = LoggerFactory.getLogger(AiAssessmentService.class);

    private final AiClient ai;
    private final MenuPickRepository repository;
    private final Clock clock;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AiAssessmentService(AiClient ai, MenuPickRepository repository, Clock clock) {
        this.ai = ai;
        this.repository = repository;
        this.clock = clock;
    }

    public boolean enabled() {
        return ai.enabled();
    }

    /** 사용자 응답을 늦추지 않도록 백그라운드에서 판별합니다. */
    public void assessAsync(Recommendation recommendation) {
        if (!ai.enabled()) {
            return;
        }
        executor.execute(() -> {
            try {
                assess(recommendation);
            } catch (RuntimeException ex) {
                log.warn("AI assessment failed for recommendation={}: {}", recommendation.id(), ex.getClass().getSimpleName());
            }
        });
    }

    /** 추천 항목이 없으면 empty, AI 호출이 실패하면 {@link AiUnavailableException}. */
    public Optional<AiAssessment> reassess(long recommendationId) {
        return repository.findRecommendationById(recommendationId).map(this::assess);
    }

    public List<AiAssessment> findFor(Collection<Long> recommendationIds) {
        return repository.findAiAssessments(recommendationIds);
    }

    private AiAssessment assess(Recommendation recommendation) {
        AiClient.AssessResult result = ai.assess(recommendation.menuName(), recommendation.reason(),
                recommendation.recommendedMenu()).orElseThrow(AiUnavailableException::new);
        AiAssessment assessment = new AiAssessment(recommendation.id(),
                Math.max(0.0, Math.min(1.0, result.riskScore())), result.riskLevel(), result.flags(),
                result.reasons(), result.engine(), clock.instant().toString());
        // 판별 중 관리자가 항목을 지웠다면 저장하지 않습니다.
        if (repository.findRecommendationById(recommendation.id()).isPresent()) {
            repository.saveAiAssessment(assessment);
        }
        return assessment;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    public static final class AiUnavailableException extends RuntimeException {
        AiUnavailableException() {
            super("AI server unavailable", null, false, false);
        }
    }
}
