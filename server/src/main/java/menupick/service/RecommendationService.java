package menupick.service;

import menupick.config.MenuPickProperties;
import menupick.domain.MenuDecision;
import menupick.domain.Recommendation;
import menupick.recommendation.RecommendationEngine;
import menupick.repository.MenuPickRepository;
import menupick.repository.MenuPickRepository.NewRecommendation;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class RecommendationService {
    /** 다른 인스턴스가 DB를 바꿨을 때를 대비한 최대 캐시 수명. 같은 서버에서의 변경은 즉시 무효화됩니다. */
    private static final Duration PUBLISHED_CACHE_TTL = Duration.ofSeconds(30);

    private final MenuPickRepository repository;
    private final RecommendationEngine engine;
    private final Clock clock;
    private final boolean monthlyLimitEnabled;

    /**
     * GET /api/recommendations/latest는 앱을 열 때마다 호출되는 가장 잦은 요청입니다.
     * 결정 설정 + 추천 단건을 매번 DB에서 읽지 않도록 스냅샷을 캐시하고, 공개 시각 판단만 매 요청 계산합니다.
     */
    private volatile PublishedSnapshot published;
    private final AtomicLong writeVersion = new AtomicLong();

    public RecommendationService(MenuPickRepository repository, RecommendationEngine engine, Clock clock,
                                 MenuPickProperties properties) {
        this.repository = repository;
        this.engine = engine;
        this.clock = clock;
        this.monthlyLimitEnabled = properties.rateLimit().monthlyRecommendation();
    }

    /** 이번 달 이미 올렸으면 empty. 월 1회 제한이 꺼져 있으면 항상 저장합니다. */
    public Optional<Recommendation> submit(String clientKey, String menuName, String reason,
                                           String recommendedMenu, String category) {
        // 사용자가 원하는 메뉴를 적었으면 그대로 씁니다. AI·규칙 엔진은 메뉴가 없을 때만 고릅니다.
        // (위험한 메뉴는 바꾸지 않고 AI 판별 결과로 관리자에게 알립니다.)
        String menu;
        if (!recommendedMenu.isBlank()) {
            menu = recommendedMenu;
        } else if (WantedMenu.isMenuName(menuName)) {
            menu = menuName.strip();
        } else {
            menu = engine.recommend(menuName, reason);
        }
        NewRecommendation input = new NewRecommendation(menuName, reason, menu, category == null ? "" : category);
        if (!monthlyLimitEnabled) {
            Recommendation saved = repository.saveRecommendation(input);
            invalidate();
            return Optional.of(saved);
        }
        String userKey = "network:" + Hashing.sha256Hex(clientKey);
        String monthKey = YearMonth.now(clock.withZone(ZoneOffset.UTC)).toString();

        Optional<Recommendation> saved = repository.saveMonthlyRecommendation(userKey, monthKey, input);
        saved.ifPresent(ignored -> invalidate());
        return saved;
    }

    /** 공개된 결정 메뉴. 결정 설정이 없으면 가장 최근 추천, 공개 전이면 empty. */
    public Optional<Recommendation> latestPublished() {
        Instant now = clock.instant();
        PublishedSnapshot snapshot = published;
        if (snapshot == null || now.isAfter(snapshot.loadedAt().plus(PUBLISHED_CACHE_TTL))) {
            long version = writeVersion.get();
            snapshot = loadSnapshot(now);
            // 읽는 도중 쓰기가 있었다면 캐시에 넣지 않습니다(오래된 스냅샷 방지).
            if (writeVersion.get() == version) {
                published = snapshot;
            }
        }
        if (snapshot.decision() != null && !snapshot.decision().isPublishedAt(now)) {
            return Optional.empty();
        }
        return Optional.ofNullable(snapshot.recommendation());
    }

    public List<Recommendation> recent(int limit) {
        return repository.findRecentRecommendations(limit);
    }

    public int delete(Collection<Long> ids) {
        int deleted = repository.deleteRecommendations(ids);
        if (deleted > 0) {
            invalidate();
        }
        return deleted;
    }

    /** 추천이 존재하지 않으면 false. */
    public boolean decide(long recommendationId, Instant publishAt) {
        if (repository.findRecommendationById(recommendationId).isEmpty()) {
            return false;
        }
        repository.saveDecision(recommendationId, publishAt.toString());
        invalidate();
        return true;
    }

    private PublishedSnapshot loadSnapshot(Instant now) {
        MenuDecision decision = repository.findDecision().orElse(null);
        Recommendation recommendation = decision == null
                ? repository.findLatestRecommendation().orElse(null)
                : repository.findRecommendationById(decision.recommendationId()).orElse(null);
        return new PublishedSnapshot(decision, recommendation, now);
    }

    private void invalidate() {
        writeVersion.incrementAndGet();
        published = null;
    }

    private record PublishedSnapshot(MenuDecision decision, Recommendation recommendation, Instant loadedAt) {
    }
}
