package menupick.db;

import menupick.model.InquiryRecord;
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
    public synchronized RecommendationRecord saveRecommendation(
            String menuName,
            String reason,
            String recommendedMenu
    ) throws IOException {
        String now = Instant.now().toString();
        String sql = "INSERT INTO recommendations (menu_name, reason, recommended_menu, created_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, menuName);
            statement.setString(2, reason);
            statement.setString(3, recommendedMenu);
            statement.setString(4, now);
            statement.executeUpdate();

            long id = 0L;
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    id = keys.getLong(1);
                }
            }
            return new RecommendationRecord(id, menuName, reason, recommendedMenu, now);
        } catch (SQLException ex) {
            throw new IOException("추천 저장 실패", ex);
        }
    }

    @Override
    public synchronized RecommendationRecord getLatestRecommendation() throws IOException {
        String sql = "SELECT id, menu_name, reason, recommended_menu, created_at FROM recommendations ORDER BY id DESC LIMIT 1";
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
                    rs.getString("created_at")
            );
        } catch (SQLException ex) {
            throw new IOException("최신 추천 조회 실패", ex);
        }
    }

    @Override
    public synchronized RecommendationRecord getRecommendationById(long id) throws IOException {
        String sql = "SELECT id, menu_name, reason, recommended_menu, created_at FROM recommendations WHERE id = ?";
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
        String sql = "SELECT id, menu_name, reason, recommended_menu, created_at FROM recommendations ORDER BY id DESC LIMIT ?";
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

        try (Statement statement = connection.createStatement()) {
            statement.execute(recommendationsSql);
            statement.execute(inquiriesSql);
            statement.execute(decisionSql);
        }
    }
}

