package menupick;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import menupick.json.JsonUtil;
import menupick.model.InquiryRecord;
import menupick.model.RecommendationRecord;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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

            RecommendationRepository recRepo = new RecommendationRepository(conn);
            InquiryRepository inqRepo = new InquiryRepository(conn);
            AdminRepository adminRepo = new AdminRepository();

            RecommendationService recService = new RecommendationService(recRepo);
            InquiryService inqService = new InquiryService(inqRepo);
            AdminService adminService = new AdminService(adminRepo);

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
            stmt.execute("CREATE TABLE IF NOT EXISTS recommendations (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "menu_name TEXT NOT NULL," +
                    "reason TEXT," +
                    "recommended_menu TEXT," +
                    "user_id TEXT," +
                    "created_at TEXT DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS inquiries (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "message TEXT NOT NULL," +
                    "admin_email TEXT," +
                    "created_at TEXT DEFAULT CURRENT_TIMESTAMP" +
                    ")");
        }
    }

    // --- Inner Classes: Repositories ---

    public static class RecommendationRepository {
        private final Connection conn;
        public RecommendationRepository(Connection conn) { this.conn = conn; }

        public RecommendationRecord save(String menuName, String reason, String recommendedMenu, String userId) throws SQLException {
            String sql = "INSERT INTO recommendations (menu_name, reason, recommended_menu, user_id) VALUES (?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, menuName);
                pstmt.setString(2, reason);
                pstmt.setString(3, recommendedMenu);
                pstmt.setString(4, userId);
                pstmt.executeUpdate();
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) return getById(rs.getLong(1));
                }
            }
            return null;
        }

        public RecommendationRecord getById(long id) throws SQLException {
            String sql = "SELECT * FROM recommendations WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, id);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) return map(rs);
                }
            }
            return null;
        }

        public RecommendationRecord findLatest() throws SQLException {
            String sql = "SELECT * FROM recommendations ORDER BY id DESC LIMIT 1";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) return map(rs);
            }
            return null;
        }

        public List<RecommendationRecord> findAll(int limit) throws SQLException {
            String sql = "SELECT * FROM recommendations ORDER BY id DESC LIMIT ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, limit);
                try (ResultSet rs = pstmt.executeQuery()) {
                    List<RecommendationRecord> list = new ArrayList<>();
                    while (rs.next()) list.add(map(rs));
                    return list;
                }
            }
        }

        public int deleteByIds(List<Long> ids) throws SQLException {
            if (ids == null || ids.isEmpty()) return 0;
            String placeholders = ids.stream().map(i -> "?").collect(Collectors.joining(","));
            String sql = "DELETE FROM recommendations WHERE id IN (" + placeholders + ")";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (int i = 0; i < ids.size(); i++) pstmt.setLong(i + 1, ids.get(i));
                return pstmt.executeUpdate();
            }
        }

        private RecommendationRecord map(ResultSet rs) throws SQLException {
            return new RecommendationRecord(
                rs.getLong("id"),
                rs.getString("menu_name"),
                rs.getString("reason"),
                rs.getString("recommended_menu"),
                rs.getString("created_at")
            );
        }
    }

    public static class InquiryRepository {
        private final Connection conn;
        public InquiryRepository(Connection conn) { this.conn = conn; }

        public InquiryRecord save(String message) throws SQLException {
            String sql = "INSERT INTO inquiries (message) VALUES (?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, message);
                pstmt.executeUpdate();
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) return getById(rs.getLong(1));
                }
            }
            return null;
        }

        public InquiryRecord getById(long id) throws SQLException {
            String sql = "SELECT * FROM inquiries WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, id);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) return map(rs);
                }
            }
            return null;
        }

        public List<InquiryRecord> findAll(int limit) throws SQLException {
            String sql = "SELECT * FROM inquiries ORDER BY id DESC LIMIT ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, limit);
                try (ResultSet rs = pstmt.executeQuery()) {
                    List<InquiryRecord> list = new ArrayList<>();
                    while (rs.next()) list.add(map(rs));
                    return list;
                }
            }
        }

        public int deleteByIds(List<Long> ids) throws SQLException {
            if (ids == null || ids.isEmpty()) return 0;
            String placeholders = ids.stream().map(i -> "?").collect(Collectors.joining(","));
            String sql = "DELETE FROM inquiries WHERE id IN (" + placeholders + ")";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (int i = 0; i < ids.size(); i++) pstmt.setLong(i + 1, ids.get(i));
                return pstmt.executeUpdate();
            }
        }

        private InquiryRecord map(ResultSet rs) throws SQLException {
            return new InquiryRecord(
                rs.getLong("id"),
                rs.getString("message"),
                rs.getString("admin_email"),
                rs.getString("created_at")
            );
        }
    }

    public static class AdminRepository {
        public boolean validate(String id) {
            String envHash = System.getenv("ADMIN_ID_HASH");
            return id != null && id.equals(envHash);
        }
    }

    // --- Inner Classes: Services ---

    public static class RecommendationService {
        private final RecommendationRepository repo;
        public RecommendationService(RecommendationRepository repo) { this.repo = repo; }
        public RecommendationRecord add(String name, String reason, String rec, String userId) throws SQLException {
            return repo.save(name, reason, rec, userId);
        }
        public RecommendationRecord getLatest() throws SQLException { return repo.findLatest(); }
        public List<RecommendationRecord> getAll(int limit) throws SQLException { return repo.findAll(limit); }
        public int delete(List<Long> ids) throws SQLException { return repo.deleteByIds(ids); }
    }

    public static class InquiryService {
        private final InquiryRepository repo;
        public InquiryService(InquiryRepository repo) { this.repo = repo; }
        public InquiryRecord add(String msg) throws SQLException { return repo.save(msg); }
        public List<InquiryRecord> getAll(int limit) throws SQLException { return repo.findAll(limit); }
        public int delete(List<Long> ids) throws SQLException { return repo.deleteByIds(ids); }
    }

    public static class AdminService {
        private final AdminRepository repo;
        public AdminService(AdminRepository repo) { this.repo = repo; }
        public String login(String id) {
            return repo.validate(id) ? UUID.randomUUID().toString() : null;
        }
    }

    // --- Inner Classes: Handlers & Middleware ---

    public static class CorsMiddleware implements HttpHandler {
        private final HttpHandler next;
        public CorsMiddleware(HttpHandler next) { this.next = next; }
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Headers h = exchange.getResponseHeaders();
            h.add("Access-Control-Allow-Origin", "*");
            h.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS, DELETE");
            h.add("Access-Control-Allow-Headers", "Content-Type, Authorization, X-MenuPick-User-Id");
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            next.handle(exchange);
        }
    }

    public abstract static class BaseHandler implements HttpHandler {
        protected void sendJson(HttpExchange exchange, int status, Object data) throws IOException {
            String json;
            if (data instanceof Map) {
                json = JsonUtil.stringify((Map<String, ?>) data);
            } else if (data instanceof List) {
                List<Map<String, ?>> maps = new ArrayList<>();
                for (Object item : (List<?>) data) {
                    if (item instanceof RecommendationRecord) maps.add(((RecommendationRecord) item).toMap());
                    else if (item instanceof InquiryRecord) maps.add(((InquiryRecord) item).toMap());
                }
                Map<String, Object> wrapper = new HashMap<>();
                wrapper.put("items", maps);
                json = JsonUtil.stringify(wrapper);
            } else {
                json = String.valueOf(data);
            }
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        }

        protected String readBody(HttpExchange exchange) throws IOException {
            return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static class RecommendationHandler extends BaseHandler {
        private final RecommendationService service;
        public RecommendationHandler(RecommendationService s) { this.service = s; }
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();
                String path = exchange.getRequestURI().getPath();
                if ("POST".equalsIgnoreCase(method)) {
                    Map<String, String> body = JsonUtil.parseFlatObject(readBody(exchange));
                    String userId = exchange.getRequestHeaders().getFirst("X-MenuPick-User-Id");
                    RecommendationRecord r = service.add(body.get("menuName"), body.get("reason"), body.get("recommendedMenu"), userId);
                    sendJson(exchange, 201, r.toMap());
                } else if ("GET".equalsIgnoreCase(method) && path.endsWith("/latest")) {
                    RecommendationRecord latest = service.getLatest();
                    if (latest == null) sendJson(exchange, 404, "{\"message\":\"not found\"}");
                    else sendJson(exchange, 200, latest.toMap());
                }
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    public static class InquiryHandler extends BaseHandler {
        private final InquiryService service;
        public InquiryHandler(InquiryService s) { this.service = s; }
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    Map<String, String> body = JsonUtil.parseFlatObject(readBody(exchange));
                    InquiryRecord i = service.add(body.get("message"));
                    sendJson(exchange, 201, i.toMap());
                }
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    public static class AdminHandler extends BaseHandler {
        private final AdminService adminService;
        private final RecommendationService recService;
        private final InquiryService inqService;

        public AdminHandler(AdminService a, RecommendationService r, InquiryService i) {
            this.adminService = a; this.recService = r; this.inqService = i;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String path = exchange.getRequestURI().getPath();
                String method = exchange.getRequestMethod();
                if (path.endsWith("/login") && "POST".equalsIgnoreCase(method)) {
                    Map<String, String> body = JsonUtil.parseFlatObject(readBody(exchange));
                    String token = adminService.login(body.get("id"));
                    if (token != null) {
                        Map<String, String> res = new HashMap<>();
                        res.put("token", token);
                        res.put("adminEmail", "admin@local");
                        res.put("expiresAt", "2099-12-31T23:59:59Z");
                        sendJson(exchange, 200, res);
                    } else sendJson(exchange, 401, "{\"message\":\"unauthorized\"}");
                } else if (path.endsWith("/recommendations")) {
                    if ("GET".equalsIgnoreCase(method)) sendJson(exchange, 200, recService.getAll(100));
                    else if ("POST".equalsIgnoreCase(method)) {
                        String body = readBody(exchange);
                        List<Long> ids = parseIds(body);
                        int count = recService.delete(ids);
                        Map<String, Object> res = new HashMap<>();
                        res.put("deleted", count);
                        sendJson(exchange, 200, res);
                    }
                } else if (path.endsWith("/inquiries")) {
                    if ("GET".equalsIgnoreCase(method)) sendJson(exchange, 200, inqService.getAll(100));
                    else if ("POST".equalsIgnoreCase(method)) {
                        List<Long> ids = parseIds(readBody(exchange));
                        int count = inqService.delete(ids);
                        Map<String, Object> res = new HashMap<>();
                        res.put("deleted", count);
                        sendJson(exchange, 200, res);
                    }
                }
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }

        private List<Long> parseIds(String body) {
            try {
                String clean = body.replaceAll("[^0-9,]", "");
                if (clean.isEmpty()) return Collections.emptyList();
                return Arrays.stream(clean.split(","))
                             .filter(s -> !s.isEmpty())
                             .map(Long::parseLong)
                             .collect(Collectors.toList());
            } catch (Exception e) { return Collections.emptyList(); }
        }
    }

    public static class HealthHandler extends BaseHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            sendJson(exchange, 200, "{\"status\":\"ok\",\"version\":\"2026-04-27\"}");
        }
    }
}
