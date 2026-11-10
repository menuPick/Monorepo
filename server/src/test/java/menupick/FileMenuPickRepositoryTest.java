package menupick;

import menupick.domain.AiAssessment;
import menupick.recommendation.RuleBasedRecommendationEngine;
import menupick.repository.FileMenuPickRepository;
import menupick.repository.MenuPickRepository.NewRecommendation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileMenuPickRepositoryTest {
    private final ObjectMapper json = JsonMapper.builder().build();

    @TempDir
    Path dir;

    @Test
    void persistsAcrossRestartAndEnforcesMonthlyLimit() {
        FileMenuPickRepository repository = new FileMenuPickRepository(dir, json);
        NewRecommendation input = new NewRecommendation("김치찌개", "따뜻한 국물", "김치찌개", "국/찌개");
        assertTrue(repository.saveMonthlyRecommendation("u1", "2026-11", input).isPresent());
        assertTrue(repository.saveMonthlyRecommendation("u1", "2026-11", input).isEmpty(), "same month blocked");
        assertTrue(repository.saveMonthlyRecommendation("u1", "2026-12", input).isPresent(), "next month allowed");
        repository.saveDecision(2, "2026-11-10T00:00:00Z");
        repository.saveInquiry("hello", "admin@local");

        FileMenuPickRepository reloaded = new FileMenuPickRepository(dir, json);
        assertEquals(2, reloaded.findRecentRecommendations(10).size());
        assertEquals(2L, reloaded.findLatestRecommendation().orElseThrow().id());
        assertEquals(2L, reloaded.findDecision().orElseThrow().recommendationId());
        assertTrue(reloaded.saveMonthlyRecommendation("u1", "2026-11", input).isEmpty(), "limit survives restart");

        assertEquals(1, reloaded.deleteRecommendations(List.of(2L, 999L)));
        assertTrue(reloaded.findDecision().isEmpty(), "decision cleared with its recommendation");
        assertEquals(1, new FileMenuPickRepository(dir, json).findRecentRecommendations(10).size());
    }

    @Test
    void readsFilesWrittenByLegacyServer() throws Exception {
        Files.writeString(dir.resolve("recommendations.jsonl"),
                "{\"id\":1,\"menuName\":\"제육볶음\",\"reason\":\"고기\",\"recommendedMenu\":\"제육볶음\",\"category\":\"\",\"createdAt\":\"2026-11-01T00:00:00Z\"}\n",
                StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("decision.json"),
                "{\"recommendationId\":1,\"publishAt\":\"2026-11-01T00:00:00Z\",\"updatedAt\":\"2026-11-01T00:00:00Z\"}",
                StandardCharsets.UTF_8);

        FileMenuPickRepository repository = new FileMenuPickRepository(dir, json);
        assertEquals("", repository.findRecommendationById(1).orElseThrow().category(), "category is not auto-classified");
        assertEquals(1L, repository.findDecision().orElseThrow().recommendationId());
    }

    @Test
    void aiAssessmentUpsertsSurvivesRestartAndIsDeletedWithRecommendation() {
        FileMenuPickRepository repository = new FileMenuPickRepository(dir, json);
        long id = repository.saveMonthlyRecommendation("u1", "2026-11",
                new NewRecommendation("떡볶이", "매운", "떡볶이", "")).orElseThrow().id();
        repository.saveAiAssessment(new AiAssessment(id, 0.2, "low", List.of(), List.of("안전"), "rule", "t1"));
        repository.saveAiAssessment(new AiAssessment(id, 0.8, "high", List.of("unhealthy"), List.of("자극적"), "claude", "t2"));

        FileMenuPickRepository reloaded = new FileMenuPickRepository(dir, json);
        List<AiAssessment> found = reloaded.findAiAssessments(List.of(id, 999L));
        assertEquals(1, found.size(), "one row per recommendation");
        assertEquals("high", found.getFirst().riskLevel(), "re-assessment overwrites");

        reloaded.deleteRecommendations(List.of(id));
        assertTrue(new FileMenuPickRepository(dir, json).findAiAssessments(List.of(id)).isEmpty());
    }

    @Test
    void ruleEngineKeepsLegacyBehavior() {
        RuleBasedRecommendationEngine engine = new RuleBasedRecommendationEngine();
        assertEquals("떡볶이", engine.recommend("", "매운 게 먹고 싶어요"));
        assertEquals("라면", engine.recommend("신라면", "매운 거"));
        assertEquals("김치찌개", engine.recommend("", "따뜻한 국물"));
        assertEquals("비빔밥", engine.recommend("", "아무거나"));
    }
}
