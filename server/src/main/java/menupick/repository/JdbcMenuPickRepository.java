package menupick.repository;

import menupick.domain.AiAssessment;
import menupick.domain.Inquiry;
import menupick.domain.MenuDecision;
import menupick.domain.Recommendation;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * MySQL/MariaDB 저장소. 단일 커넥션 + synchronized 대신 HikariCP 커넥션 풀을 사용해 요청을 병렬로 처리합니다.
 * 테이블 구조는 기존 서버와 동일하게 유지합니다.
 */
public final class JdbcMenuPickRepository implements MenuPickRepository {
    private static final String RECOMMENDATION_COLUMNS = "id, menu_name, reason, recommended_menu, category, created_at";
    private static final String INQUIRY_COLUMNS = "id, message, admin_email, created_at";

    private static final RowMapper<Recommendation> RECOMMENDATION_ROW = (rs, rowNum) -> new Recommendation(
            rs.getLong("id"),
            rs.getString("menu_name"),
            rs.getString("reason"),
            rs.getString("recommended_menu"),
            rs.getString("category"),
            rs.getString("created_at"));

    private static final RowMapper<Inquiry> INQUIRY_ROW = (rs, rowNum) -> new Inquiry(
            rs.getLong("id"),
            rs.getString("message"),
            rs.getString("admin_email"),
            rs.getString("created_at"));

    private static final RowMapper<MenuDecision> DECISION_ROW = (rs, rowNum) -> new MenuDecision(
            rs.getLong("recommendation_id"),
            rs.getString("publish_at"),
            rs.getString("updated_at"));

    private static final RowMapper<AiAssessment> AI_ASSESSMENT_ROW = (rs, rowNum) -> new AiAssessment(
            rs.getLong("recommendation_id"),
            rs.getDouble("risk_score"),
            rs.getString("risk_level"),
            splitLines(rs.getString("flags")),
            splitLines(rs.getString("reasons")),
            rs.getString("engine"),
            rs.getString("assessed_at"));

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final TransactionTemplate tx;

    public JdbcMenuPickRepository(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
        this.tx = tx;
        initializeSchema();
    }

    @Override
    public Optional<Recommendation> saveMonthlyRecommendation(String userKey, String monthKey, NewRecommendation input) {
        String now = Instant.now().toString();
        return tx.execute(status -> {
            // 중복이면 0행 → 예외/롤백 없이 바로 거절합니다.
            int claimed = jdbc.update(
                    "INSERT IGNORE INTO recommendation_monthly_limits (user_key, month_key, created_at) VALUES (?, ?, ?)",
                    userKey, monthKey, now);
            if (claimed == 0) {
                return Optional.empty();
            }
            long id = insertReturningId(
                    "INSERT INTO recommendations (menu_name, reason, recommended_menu, category, created_at) VALUES (?, ?, ?, ?, ?)",
                    input.menuName(), input.reason(), input.recommendedMenu(), input.category(), now);
            return Optional.of(new Recommendation(id, input.menuName(), input.reason(),
                    input.recommendedMenu(), input.category(), now));
        });
    }

    @Override
    public Recommendation saveRecommendation(NewRecommendation input) {
        String now = Instant.now().toString();
        long id = insertReturningId(
                "INSERT INTO recommendations (menu_name, reason, recommended_menu, category, created_at) VALUES (?, ?, ?, ?, ?)",
                input.menuName(), input.reason(), input.recommendedMenu(), input.category(), now);
        return new Recommendation(id, input.menuName(), input.reason(), input.recommendedMenu(), input.category(), now);
    }

    @Override
    public Optional<Recommendation> findLatestRecommendation() {
        return first(jdbc.query("SELECT " + RECOMMENDATION_COLUMNS + " FROM recommendations ORDER BY id DESC LIMIT 1",
                RECOMMENDATION_ROW));
    }

    @Override
    public Optional<Recommendation> findRecommendationById(long id) {
        return first(jdbc.query("SELECT " + RECOMMENDATION_COLUMNS + " FROM recommendations WHERE id = ?",
                RECOMMENDATION_ROW, id));
    }

    @Override
    public List<Recommendation> findRecentRecommendations(int limit) {
        return jdbc.query("SELECT " + RECOMMENDATION_COLUMNS + " FROM recommendations ORDER BY id DESC LIMIT ?",
                RECOMMENDATION_ROW, limit);
    }

    @Override
    public int deleteRecommendations(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        MapSqlParameterSource params = new MapSqlParameterSource("ids", ids);
        Integer deleted = tx.execute(status -> {
            named.update("DELETE FROM menu_decision_settings WHERE id = 1 AND recommendation_id IN (:ids)", params);
            named.update("DELETE FROM ai_assessments WHERE recommendation_id IN (:ids)", params);
            return named.update("DELETE FROM recommendations WHERE id IN (:ids)", params);
        });
        return deleted == null ? 0 : deleted;
    }

    @Override
    public Optional<MenuDecision> findDecision() {
        return first(jdbc.query(
                "SELECT recommendation_id, publish_at, updated_at FROM menu_decision_settings WHERE id = 1",
                DECISION_ROW));
    }

    @Override
    public void saveDecision(long recommendationId, String publishAt) {
        jdbc.update("REPLACE INTO menu_decision_settings (id, recommendation_id, publish_at, updated_at) VALUES (1, ?, ?, ?)",
                recommendationId, publishAt, Instant.now().toString());
    }

    @Override
    public Inquiry saveInquiry(String message, String adminEmail) {
        String now = Instant.now().toString();
        long id = insertReturningId("INSERT INTO inquiries (message, admin_email, created_at) VALUES (?, ?, ?)",
                message, adminEmail, now);
        return new Inquiry(id, message, adminEmail, now);
    }

    @Override
    public Optional<Inquiry> findLatestInquiry() {
        return first(jdbc.query("SELECT " + INQUIRY_COLUMNS + " FROM inquiries ORDER BY id DESC LIMIT 1", INQUIRY_ROW));
    }

    @Override
    public List<Inquiry> findRecentInquiries(int limit) {
        return jdbc.query("SELECT " + INQUIRY_COLUMNS + " FROM inquiries ORDER BY id DESC LIMIT ?", INQUIRY_ROW, limit);
    }

    @Override
    public int deleteInquiries(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        return named.update("DELETE FROM inquiries WHERE id IN (:ids)", new MapSqlParameterSource("ids", ids));
    }

    @Override
    public void saveAiAssessment(AiAssessment a) {
        jdbc.update("REPLACE INTO ai_assessments (recommendation_id, risk_score, risk_level, flags, reasons, engine, assessed_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                a.recommendationId(), a.riskScore(), a.riskLevel(), joinLines(a.flags()), joinLines(a.reasons()),
                a.engine(), a.assessedAt());
    }

    @Override
    public List<AiAssessment> findAiAssessments(Collection<Long> recommendationIds) {
        if (recommendationIds.isEmpty()) {
            return List.of();
        }
        return named.query("SELECT recommendation_id, risk_score, risk_level, flags, reasons, engine, assessed_at "
                        + "FROM ai_assessments WHERE recommendation_id IN (:ids) ORDER BY recommendation_id DESC",
                new MapSqlParameterSource("ids", recommendationIds), AI_ASSESSMENT_ROW);
    }

    // ---- 내부 구현 ----

    /** 목록 컬럼은 한 줄에 하나씩 저장합니다(항목 안의 줄바꿈은 공백으로 바꿈). */
    private static String joinLines(List<String> values) {
        return String.join("\n", values.stream().map(v -> v.replace('\r', ' ').replace('\n', ' ')).toList());
    }

    private static List<String> splitLines(String value) {
        return value == null || value.isEmpty() ? List.of() : List.of(value.split("\n"));
    }

    private long insertReturningId(String sql, Object... args) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            return statement;
        }, keys);
        Number key = keys.getKey();
        return key == null ? 0L : key.longValue();
    }

    private static <T> Optional<T> first(List<T> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    private void initializeSchema() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS recommendations (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    menu_name VARCHAR(255) NOT NULL,
                    reason TEXT NOT NULL,
                    recommended_menu VARCHAR(255) NOT NULL,
                    category VARCHAR(64) NOT NULL DEFAULT '',
                    created_at VARCHAR(64) NOT NULL
                )
                """);
        // 오래된 DB에는 category 컬럼이 없을 수 있습니다(이미 있으면 "중복 컬럼" 오류 → 무시).
        ignoreDuplicateColumn("ALTER TABLE recommendations ADD COLUMN category VARCHAR(64) NOT NULL DEFAULT '' AFTER recommended_menu");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS inquiries (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    message TEXT NOT NULL,
                    admin_email VARCHAR(255) NOT NULL,
                    created_at VARCHAR(64) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS menu_decision_settings (
                    id INT PRIMARY KEY,
                    recommendation_id BIGINT NOT NULL,
                    publish_at VARCHAR(64) NOT NULL,
                    updated_at VARCHAR(64) NOT NULL
                )
                """);
        // AI 위험 판별 결과(추천당 1행, 재평가 시 REPLACE).
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ai_assessments (
                    recommendation_id BIGINT PRIMARY KEY,
                    risk_score DOUBLE NOT NULL,
                    risk_level VARCHAR(16) NOT NULL,
                    flags TEXT NOT NULL,
                    reasons TEXT NOT NULL,
                    engine VARCHAR(32) NOT NULL,
                    assessed_at VARCHAR(64) NOT NULL
                )
                """);
        // (user_key, month_key) PRIMARY KEY가 월 1회 제한의 유일성을 보장합니다.
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS recommendation_monthly_limits (
                    user_key VARCHAR(128) NOT NULL,
                    month_key VARCHAR(7) NOT NULL,
                    created_at VARCHAR(64) NOT NULL,
                    PRIMARY KEY (user_key, month_key)
                )
                """);
    }

    /** 중복 컬럼 오류만 무시합니다. MySQL/MariaDB는 오류 코드 1060, H2는 SQLState 42S21. */
    private void ignoreDuplicateColumn(String sql) {
        try {
            jdbc.execute(sql);
        } catch (DataAccessException ex) {
            boolean duplicate = ex.getMostSpecificCause() instanceof SQLException sqlEx
                    && (sqlEx.getErrorCode() == 1060 || "42S21".equals(sqlEx.getSQLState()));
            if (!duplicate) {
                throw ex;
            }
        }
    }
}
