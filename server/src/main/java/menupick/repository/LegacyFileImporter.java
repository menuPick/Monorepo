package menupick.repository;

import menupick.domain.AiAssessment;
import menupick.domain.Inquiry;
import menupick.domain.MenuDecision;
import menupick.domain.Recommendation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 예전 로컬 파일 저장소(data/*.jsonl)를 H2로 한 번 옮깁니다.
 * H2에 추천·문의가 하나라도 있으면 아무것도 하지 않으므로 여러 번 실행해도 안전합니다.
 * 원본 파일은 지우지 않습니다. 월 1회 제한 기록은 옮기지 않습니다(로컬은 제한을 끄고 씀).
 */
public final class LegacyFileImporter {
    private static final Logger log = LoggerFactory.getLogger(LegacyFileImporter.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper json;

    public LegacyFileImporter(JdbcTemplate jdbc, TransactionTemplate tx, ObjectMapper json) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.json = json;
    }

    public void importIfEmpty(Path storageDir) {
        if (!Files.exists(storageDir.resolve("recommendations.jsonl")) && !Files.exists(storageDir.resolve("inquiries.jsonl"))) {
            return;
        }
        Long existing = jdbc.queryForObject(
                "SELECT (SELECT COUNT(*) FROM recommendations) + (SELECT COUNT(*) FROM inquiries)", Long.class);
        if (existing != null && existing > 0) {
            return;
        }

        FileMenuPickRepository legacy = new FileMenuPickRepository(storageDir, json);
        List<Recommendation> recommendations = legacy.findRecentRecommendations(Integer.MAX_VALUE);
        List<Inquiry> inquiries = legacy.findRecentInquiries(Integer.MAX_VALUE);
        List<AiAssessment> assessments = legacy.findAiAssessments(recommendations.stream().map(Recommendation::id).toList());
        MenuDecision decision = legacy.findDecision().orElse(null);

        tx.executeWithoutResult(status -> {
            for (Recommendation r : recommendations.reversed()) {
                jdbc.update("INSERT INTO recommendations (id, menu_name, reason, recommended_menu, category, created_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?)",
                        r.id(), r.menuName(), r.reason(), r.recommendedMenu(), r.category(), r.createdAt());
            }
            for (Inquiry i : inquiries.reversed()) {
                jdbc.update("INSERT INTO inquiries (id, message, admin_email, created_at) VALUES (?, ?, ?, ?)",
                        i.id(), i.message(), i.adminEmail(), i.createdAt());
            }
            JdbcMenuPickRepository target = new JdbcMenuPickRepository(jdbc, tx);
            assessments.forEach(target::saveAiAssessment);
            if (decision != null) {
                jdbc.update("REPLACE INTO menu_decision_settings (id, recommendation_id, publish_at, updated_at) VALUES (1, ?, ?, ?)",
                        decision.recommendationId(), decision.publishAt(), decision.updatedAt());
            }
            // id를 직접 넣었으므로 다음 자동 번호를 그 뒤로 맞춥니다.
            restartIdentity("recommendations", recommendations.stream().mapToLong(Recommendation::id).max().orElse(0));
            restartIdentity("inquiries", inquiries.stream().mapToLong(Inquiry::id).max().orElse(0));
        });
        log.info("Imported legacy file data into H2: recommendations={}, inquiries={}, aiAssessments={}",
                recommendations.size(), inquiries.size(), assessments.size());
    }

    private void restartIdentity(String table, long maxId) {
        jdbc.execute("ALTER TABLE " + table + " ALTER COLUMN id RESTART WITH " + (maxId + 1));
    }
}
