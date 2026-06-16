package menupick.db;

import menupick.model.InquiryRecord;
import menupick.model.MenuCategoryClassifier;
import menupick.model.MenuDecisionSetting;
import menupick.model.RecommendationRecord;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public final class MySqlDatabase implements DatabaseRepository {
    private final Connection connection;

    public MySqlDatabase(String dbUrl, String user, String password) throws IOException {
        try {
            this.connection = DriverManager.getConnection(dbUrl, user, password);
            initializeSchema();
        } catch (SQLException ex) {
            throw new IOException("MySQL 연결 실패: " + ex.getMessage(), ex);
        }
    }

    @Override
    public synchronized RecommendationRecord saveMonthlyRecommendation(
            String userKey,
            String monthKey,
            String menuName,
            String reason,
            String recommendedMenu,
            String category
    ) throws IOException {
        String now = Instant.now().toString();
        String normalizedCategory = MenuCategoryClassifier.normalize(category, menuName, recommendedMenu);
        boolean prevAutoCommit;
        try {
            prevAutoCommit = connection.getAutoCommit();
        } catch (SQLException ex) {
            throw new IOException("트랜잭션 설정 실패", ex);
        }

        try {
            connection.setAutoCommit(false);

            String limitSql = "INSERT INTO recommendation_monthly_limits (user_key, month_key, created_at) VALUES (?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(limitSql)) {
                statement.setString(1, userKey);
                statement.setString(2, monthKey);
                statement.setString(3, now);
                statement.executeUpdate();
            }

            String sql = "INSERT INTO recommendations (menu_name, reason, recommended_menu, category, created_at) VALUES (?, ?, ?, ?, ?)";
            RecommendationRecord record;
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, menuName);
                statement.setString(2, reason);
                statement.setString(3, recommendedMenu);
                statement.setString(4, normalizedCategory);
                statement.setString(5, now);
                statement.executeUpdate();

                long id = 0L;
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        id = keys.getLong(1);
                    }
                }
                record = new RecommendationRecord(id, menuName, reason, recommendedMenu, normalizedCategory, now);
            }

            connection.commit();
            return record;
        } catch (SQLException ex) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
            }
            if ("23000".equals(ex.getSQLState())) {
                return null;
            }
            throw new IOException("추천 저장 실패", ex);
        } finally {
            try {
                connection.setAutoCommit(prevAutoCommit);
            } catch (SQLException ignored) {
            }
        }
    }

    @Override
    public synchronized RecommendationRecord saveRecommendation(
            String menuName,
            String reason,
            String recommendedMenu,
            String category
    ) throws IOException {
        String now = Instant.now().toString();
        String normalizedCategory = MenuCategoryClassifier.normalize(category, menuName, recommendedMenu);
        String sql = "INSERT INTO recommendations (menu_name, reason, recommended_menu, category, created_at) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, menuName);
            statement.setString(2, reason);
            statement.setString(3, recommendedMenu);
            statement.setString(4, normalizedCategory);
            statement.setString(5, now);
            statement.executeUpdate();

            long id = 0L;
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    id = keys.getLong(1);
                }
            }
            return new RecommendationRecord(id, menuName, reason, recommendedMenu, normalizedCategory, now);
        } catch (SQLException ex) {
            throw new IOException("추천 저장 실패", ex);
        }
    }

    @Override
    public synchronized RecommendationRecord getLatestRecommendation() throws IOException {
        String sql = "SELECT id, menu_name, reason, recommended_menu, category, created_at FROM recommendations ORDER BY id DESC LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            if (!rs.next()) {
                return null;
            }
            return new RecommendationRecord(
                    rs.getLong("id"),
                    rs.getString("menu_name"),
                    rs.getString("reason"),
                    rs.getString("recommended_menu"),
                    rs.getString("category"),
                    rs.getString("created_at")
            );
        } catch (SQLException ex) {
            throw new IOException("최신 추천 조회 실패", ex);
        }
    }

    @Override
    public synchronized RecommendationRecord getRecommendationById(long id) throws IOException {
        String sql = "SELECT id, menu_name, reason, recommended_menu, category, created_at FROM recommendations WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new RecommendationRecord(
                        rs.getLong("id"),
                        rs.getString("menu_name"),
                        rs.getString("reason"),
                        rs.getString("recommended_menu"),
                        rs.getString("category"),
                        rs.getString("created_at")
                );
            }
        } catch (SQLException ex) {
            throw new IOException("추천 단건 조회 실패", ex);
        }
    }

    @Override
    public synchronized MenuDecisionSetting getMenuDecisionSetting() throws IOException {
        String sql = "SELECT recommendation_id, publish_at, updated_at FROM menu_decision_settings WHERE id = 1";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            if (!rs.next()) {
                return null;
            }
            return new MenuDecisionSetting(
                    rs.getLong("recommendation_id"),
                    rs.getString("publish_at"),
                    rs.getString("updated_at")
            );
        } catch (SQLException ex) {
            throw new IOException("결정 설정 조회 실패", ex);
        }
    }

    @Override
    public synchronized void upsertMenuDecisionSetting(long recommendationId, String publishAt) throws IOException {
        String now = Instant.now().toString();
        String sql = "REPLACE INTO menu_decision_settings (id, recommendation_id, publish_at, updated_at) VALUES (1, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, recommendationId);
            statement.setString(2, publishAt);
            statement.setString(3, now);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IOException("결정 설정 저장 실패", ex);
        }
    }

    @Override
    public synchronized InquiryRecord saveInquiry(String message, String adminEmail) throws IOException {
        String now = Instant.now().toString();
        String sql = "INSERT INTO inquiries (message, admin_email, created_at) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, message);
            statement.setString(2, adminEmail);
            statement.setString(3, now);
            statement.executeUpdate();

            long id = 0L;
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    id = keys.getLong(1);
                }
            }
            return new InquiryRecord(id, message, adminEmail, now);
        } catch (SQLException ex) {
            throw new IOException("문의 저장 실패", ex);
        }
    }

    @Override
    public synchronized InquiryRecord getLatestInquiry() throws IOException {
        String sql = "SELECT id, message, admin_email, created_at FROM inquiries ORDER BY id DESC LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            if (!rs.next()) {
                return null;
            }
            return new InquiryRecord(
                    rs.getLong("id"),
                    rs.getString("message"),
                    rs.getString("admin_email"),
                    rs.getString("created_at")
            );
        } catch (SQLException ex) {
            throw new IOException("최신 문의 조회 실패", ex);
        }
    }

    @Override
    public synchronized List<RecommendationRecord> listRecommendations(int limit) throws IOException {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        String sql = "SELECT id, menu_name, reason, recommended_menu, category, created_at FROM recommendations ORDER BY id DESC LIMIT ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, safeLimit);
            try (ResultSet rs = statement.executeQuery()) {
                List<RecommendationRecord> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new RecommendationRecord(
                            rs.getLong("id"),
                            rs.getString("menu_name"),
                            rs.getString("reason"),
                            rs.getString("recommended_menu"),
                            rs.getString("category"),
                            rs.getString("created_at")
                    ));
                }
                return list;
            }
        } catch (SQLException ex) {
            throw new IOException("추천 목록 조회 실패", ex);
        }
    }

    @Override
    public synchronized List<InquiryRecord> listInquiries(int limit) throws IOException {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        String sql = "SELECT id, message, admin_email, created_at FROM inquiries ORDER BY id DESC LIMIT ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, safeLimit);
            try (ResultSet rs = statement.executeQuery()) {
                List<InquiryRecord> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new InquiryRecord(
                            rs.getLong("id"),
                            rs.getString("message"),
                            rs.getString("admin_email"),
                            rs.getString("created_at")
                    ));
                }
                return list;
            }
        } catch (SQLException ex) {
            throw new IOException("문의 목록 조회 실패", ex);
        }
    }

    @Override
    public synchronized int deleteRecommendations(List<Long> ids) throws IOException {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        // IN (?, ?, ...) 구성
        StringJoiner joiner = new StringJoiner(",");
        for (int i = 0; i < ids.size(); i++) {
            joiner.add("?");
        }
        String placeholders = joiner.toString();

        boolean prevAutoCommit;
        try {
            prevAutoCommit = connection.getAutoCommit();
        } catch (SQLException ex) {
            throw new IOException("트랜잭션 설정 실패", ex);
        }

        try {
            connection.setAutoCommit(false);

            // 결정 메뉴가 삭제 대상에 포함되면 설정도 같이 제거
            String deleteDecisionSql = "DELETE FROM menu_decision_settings WHERE id = 1 AND recommendation_id IN (" + placeholders + ")";
            try (PreparedStatement statement = connection.prepareStatement(deleteDecisionSql)) {
                for (int i = 0; i < ids.size(); i++) {
                    statement.setLong(i + 1, ids.get(i));
                }
                statement.executeUpdate();
            }

            String deleteSql = "DELETE FROM recommendations WHERE id IN (" + placeholders + ")";
            int deleted;
            try (PreparedStatement statement = connection.prepareStatement(deleteSql)) {
                for (int i = 0; i < ids.size(); i++) {
                    statement.setLong(i + 1, ids.get(i));
                }
                deleted = statement.executeUpdate();
            }

            connection.commit();
            return deleted;
        } catch (SQLException ex) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
            }
            throw new IOException("추천 삭제 실패", ex);
        } finally {
            try {
                connection.setAutoCommit(prevAutoCommit);
            } catch (SQLException ignored) {
            }
        }
    }

    @Override
    public synchronized int deleteInquiries(List<Long> ids) throws IOException {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        StringJoiner joiner = new StringJoiner(",");
        for (int i = 0; i < ids.size(); i++) {
            joiner.add("?");
        }
        String placeholders = joiner.toString();

        String deleteSql = "DELETE FROM inquiries WHERE id IN (" + placeholders + ")";
        try (PreparedStatement statement = connection.prepareStatement(deleteSql)) {
            for (int i = 0; i < ids.size(); i++) {
                statement.setLong(i + 1, ids.get(i));
            }
            return statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IOException("문의 삭제 실패", ex);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        try {
            connection.close();
        } catch (SQLException ex) {
            throw new IOException("MySQL 연결 종료 실패", ex);
        }
    }

    private void initializeSchema() throws SQLException {
        String recommendationsSql = """
                CREATE TABLE IF NOT EXISTS recommendations (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    menu_name VARCHAR(255) NOT NULL,
                    reason TEXT NOT NULL,
                    recommended_menu VARCHAR(255) NOT NULL,
                    category VARCHAR(64) NOT NULL DEFAULT '미분류',
                    created_at VARCHAR(64) NOT NULL
                )
                """;

        String inquiriesSql = """
                CREATE TABLE IF NOT EXISTS inquiries (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    message TEXT NOT NULL,
                    admin_email VARCHAR(255) NOT NULL,
                    created_at VARCHAR(64) NOT NULL
                )
                """;

        String decisionSql = """
                CREATE TABLE IF NOT EXISTS menu_decision_settings (
                    id INT PRIMARY KEY,
                    recommendation_id BIGINT NOT NULL,
                    publish_at VARCHAR(64) NOT NULL,
                    updated_at VARCHAR(64) NOT NULL
                )
                """;

        String monthlyLimitSql = """
                CREATE TABLE IF NOT EXISTS recommendation_monthly_limits (
                    user_key VARCHAR(128) NOT NULL,
                    month_key VARCHAR(7) NOT NULL,
                    created_at VARCHAR(64) NOT NULL,
                    PRIMARY KEY (user_key, month_key)
                )
                """;

        try (Statement statement = connection.createStatement()) {
            statement.execute(recommendationsSql);
            ensureRecommendationCategoryColumn(statement);
            backfillRecommendationCategories(statement);
            statement.execute(inquiriesSql);
            statement.execute(decisionSql);
            statement.execute(monthlyLimitSql);
            try {
                statement.execute(
                        "ALTER TABLE recommendation_monthly_limits " +
                                "ADD UNIQUE KEY uniq_user_month (user_key, month_key)"
                );
            } catch (SQLException ex) {
                // Ignore if the unique key already exists.
                if (!"42S21".equals(ex.getSQLState()) && ex.getErrorCode() != 1061) {
                    throw ex;
                }
            }
        }
    }

    private void ensureRecommendationCategoryColumn(Statement statement) throws SQLException {
        try {
            statement.execute("ALTER TABLE recommendations ADD COLUMN category VARCHAR(64) NOT NULL DEFAULT '미분류' AFTER recommended_menu");
        } catch (SQLException ex) {
            // Ignore if the column already exists.
            if (!"42S21".equals(ex.getSQLState()) && ex.getErrorCode() != 1060) {
                throw ex;
            }
        }
    }

    private void backfillRecommendationCategories(Statement statement) throws SQLException {
        statement.executeUpdate("""
                UPDATE recommendations
                SET category = CASE
                    WHEN CONCAT(menu_name, ' ', recommended_menu) LIKE '%볶음밥%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%덮밥%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%초밥%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%유부초밥%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%파스타%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%국밥%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%밥%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%죽%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%국수%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%라면%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%냉면%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%비빔면%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%짜장면%' THEN '주식'
                    WHEN CONCAT(menu_name, ' ', recommended_menu) LIKE '%찌개%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%마라탕%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%탕%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%국%' THEN '국/찌개'
                    WHEN CONCAT(menu_name, ' ', recommended_menu) LIKE '%김치%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%깍두기%' THEN '김치류'
                    WHEN CONCAT(menu_name, ' ', recommended_menu) LIKE '%고기%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%제육%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%생선%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%명태%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%강정%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%닭%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%치킨%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%황금올리브%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%돼지%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%소고기%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%스테이크%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%새우%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%쉬림프%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%연어%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%육회%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%육바연%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%홍어%' THEN '주찬(메인 반찬)'
                    WHEN CONCAT(menu_name, ' ', recommended_menu) LIKE '%감자채%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%감자튀김%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%채소%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%무침%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%조림%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%전%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%볶음%' THEN '부찬(보조 반찬)'
                    WHEN CONCAT(menu_name, ' ', recommended_menu) LIKE '%과일%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%음료%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%떡%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%과자%'
                      OR CONCAT(menu_name, ' ', recommended_menu) LIKE '%디저트%' THEN '후식(디저트)'
                    ELSE '미분류'
                END
                WHERE category IS NULL OR category = '' OR category = '미분류'
                """);
    }
}
