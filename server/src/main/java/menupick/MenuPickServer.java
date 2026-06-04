package menupick;

import com.sun.net.httpserver.HttpServer;
import menupick.handler.AdminHandler;
import menupick.handler.InquiryHandler;
import menupick.handler.RecommendationHandler;
import menupick.handler.HealthHandler;
import menupick.middleware.CorsMiddleware;
import menupick.repository.AdminRepository;
import menupick.repository.InquiryRepository;
import menupick.repository.RecommendationRepository;
import menupick.service.AdminService;
import menupick.service.InquiryService;
import menupick.service.RecommendationService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class MenuPickServer {
    private static final Logger logger = Logger.getLogger(MenuPickServer.class.getName());

    public static void main(String[] args) {
        String dbUrl = System.getenv("DB_URL");
        if (dbUrl == null || dbUrl.isEmpty()) {
            dbUrl = "jdbc:sqlite:menupick.db";
        }

        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            logger.info("Connected to database: " + dbUrl);
            initializeDatabase(conn);

            // 리포지토리 및 서비스 초기화
            RecommendationRepository recRepo = new RecommendationRepository(conn);
            InquiryRepository inqRepo = new InquiryRepository(conn);
            AdminRepository adminRepo = new AdminRepository(conn);

            RecommendationService recService = new RecommendationService(recRepo);
            InquiryService inqService = new InquiryService(inqRepo);
            AdminService adminService = new AdminService(adminRepo);

            // 포트 설정 (기본값 80)
            int port = 80;
            String portEnv = System.getenv("PORT");
            if (portEnv != null && !portEnv.isEmpty()) {
                try {
                    port = Integer.parseInt(portEnv);
                } catch (NumberFormatException e) {
                    logger.warning("Invalid PORT env: " + portEnv + ". Using default: " + port);
                }
            }

            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            // 핸들러 등록 및 CORS 미들웨어 적용
            server.createContext("/api/recommendations", new CorsMiddleware(new RecommendationHandler(recService)));
            server.createContext("/api/inquiries", new CorsMiddleware(new InquiryHandler(inqService)));
            server.createContext("/api/admin", new CorsMiddleware(new AdminHandler(adminService, recService, inqService)));
            server.createContext("/health", new CorsMiddleware(new HealthHandler()));

            server.setExecutor(Executors.newFixedThreadPool(10));
            server.start();

            logger.info("MenuPick Server started on port " + port);
            logger.info("Health check available at http://localhost:" + port + "/health");

        } catch (SQLException | IOException e) {
            logger.severe("Server failed to start: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void initializeDatabase(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // 추천 테이블
            stmt.execute("CREATE TABLE IF NOT EXISTS recommendations (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "menu_name TEXT NOT NULL," +
                    "reason TEXT," +
                    "recommended_menu TEXT," +
                    "user_id TEXT," +
                    "publish_at TEXT," +
                    "created_at TEXT DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            // 문의 테이블
            stmt.execute("CREATE TABLE IF NOT EXISTS inquiries (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "message TEXT NOT NULL," +
                    "created_at TEXT DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            // 관리자 테이블
            stmt.execute("CREATE TABLE IF NOT EXISTS admins (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "email TEXT UNIQUE NOT NULL," +
                    "password_hash TEXT NOT NULL" +
                    ")");

            // 결정 메뉴(Decision) 관리 테이블 (필요 시)
            stmt.execute("CREATE TABLE IF NOT EXISTS decisions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "recommendation_id INTEGER UNIQUE," +
                    "publish_at TEXT NOT NULL," +
                    "FOREIGN KEY(recommendation_id) REFERENCES recommendations(id)" +
                    ")");
        }
    }
}
