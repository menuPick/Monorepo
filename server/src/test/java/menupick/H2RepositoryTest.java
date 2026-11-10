package menupick;

import com.zaxxer.hikari.HikariDataSource;
import menupick.domain.AiAssessment;
import menupick.repository.FileMenuPickRepository;
import menupick.repository.JdbcMenuPickRepository;
import menupick.repository.LegacyFileImporter;
import menupick.repository.MenuPickRepository.NewRecommendation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** JdbcMenuPickRepository(MySQL용 SQL)를 H2 MySQL 호환 모드 파일 DB에서 검증합니다. */
class H2RepositoryTest {
    private final ObjectMapper json = JsonMapper.builder().build();
    private final List<HikariDataSource> opened = new ArrayList<>();

    @TempDir
    Path dir;

    private JdbcTemplate jdbc() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:file:" + dir.resolve("menupick") + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        ds.setUsername("sa");
        ds.setMaximumPoolSize(2);
        opened.add(ds);
        return new JdbcTemplate(ds);
    }

    private TransactionTemplate tx(JdbcTemplate jdbc) {
        return new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    }

    private JdbcMenuPickRepository open() {
        JdbcTemplate jdbc = jdbc();
        return new JdbcMenuPickRepository(jdbc, tx(jdbc));
    }

    @AfterEach
    void close() {
        opened.forEach(HikariDataSource::close);
    }

    @Test
    void sqlWorksOnH2AndSurvivesRestart() {
        JdbcMenuPickRepository repository = open();
        NewRecommendation input = new NewRecommendation("김치찌개", "따뜻한 국물", "김치찌개", "");
        assertTrue(repository.saveMonthlyRecommendation("u1", "2026-11", input).isPresent());
        assertTrue(repository.saveMonthlyRecommendation("u1", "2026-11", input).isEmpty(), "INSERT IGNORE blocks same month");
        long free = repository.saveRecommendation(input).id();
        repository.saveDecision(free, "2026-11-10T00:00:00Z");
        repository.saveDecision(free, "2026-11-11T00:00:00Z"); // REPLACE INTO
        repository.saveAiAssessment(new AiAssessment(free, 0.2, "low", List.of(), List.of("a"), "rule", "t1"));
        repository.saveAiAssessment(new AiAssessment(free, 0.9, "high", List.of("not_food", "allergen"),
                List.of("첫째", "둘째"), "llm", "t2"));
        repository.saveInquiry("문의", "admin@local");
        close();

        JdbcMenuPickRepository reopened = open(); // 스키마 재실행(중복 컬럼 무시) 포함
        assertEquals(2, reopened.findRecentRecommendations(10).size());
        assertEquals("2026-11-11T00:00:00Z", reopened.findDecision().orElseThrow().publishAt());
        AiAssessment ai = reopened.findAiAssessments(List.of(free)).getFirst();
        assertEquals(List.of("not_food", "allergen"), ai.flags());
        assertEquals(List.of("첫째", "둘째"), ai.reasons());
        assertEquals(1, reopened.findRecentInquiries(10).size());

        assertEquals(1, reopened.deleteRecommendations(List.of(free)));
        assertTrue(reopened.findDecision().isEmpty());
        assertTrue(reopened.findAiAssessments(List.of(free)).isEmpty());
    }

    @Test
    void importsLegacyFilesOnceKeepingIds() {
        FileMenuPickRepository legacy = new FileMenuPickRepository(dir, json);
        legacy.saveRecommendation(new NewRecommendation("떡볶이", "매운", "떡볶이", ""));
        long second = legacy.saveRecommendation(new NewRecommendation("비빔밥", "채소", "비빔밥", "")).id();
        legacy.saveDecision(second, "2026-11-10T00:00:00Z");
        legacy.saveAiAssessment(new AiAssessment(second, 0.1, "low", List.of(), List.of("안전"), "rule", "t"));
        legacy.saveInquiry("예전 문의", "admin@local");

        JdbcTemplate jdbc = jdbc();
        JdbcMenuPickRepository repository = new JdbcMenuPickRepository(jdbc, tx(jdbc));
        LegacyFileImporter importer = new LegacyFileImporter(jdbc, tx(jdbc), json);
        importer.importIfEmpty(dir);
        importer.importIfEmpty(dir); // 두 번째는 아무것도 안 함

        assertEquals(2, repository.findRecentRecommendations(10).size());
        assertEquals("비빔밥", repository.findRecommendationById(second).orElseThrow().menuName(), "ids kept");
        assertEquals(second, repository.findDecision().orElseThrow().recommendationId());
        assertEquals(1, repository.findAiAssessments(List.of(second)).size());
        assertEquals(1, repository.findRecentInquiries(10).size());
        assertEquals(3L, repository.saveRecommendation(new NewRecommendation("라면", "면", "라면", "")).id(),
                "auto id continues after imported ids");
    }
}
