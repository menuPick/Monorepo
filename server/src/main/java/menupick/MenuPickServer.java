package menupick;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import menupick.db.DatabaseRepository;
import menupick.db.MySqlDatabase;
import menupick.json.JsonUtil;
import menupick.model.InquiryRecord;
import menupick.model.MenuDecisionSetting;
import menupick.model.RecommendationRecord;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

public final class MenuPickServer {
    public static final String ADMIN_EMAIL = "junsumon090608@dgsw.hs.kr";
    public static final String DEFAULT_DB_URL = "jdbc:mysql://localhost:3306/menupick?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&createDatabaseIfNotExist=true";
    public static final String DEFAULT_ADMIN_ID_HASH_FILE = "secrets/admin_id_hash.txt";
    private static final String SERVER_VERSION = "2026-04-27";
    private static final Duration ADMIN_TOKEN_TTL = Duration.ofHours(2);
    private static final Duration ADMIN_LOGIN_WINDOW = Duration.ofMinutes(15);
    private static final int ADMIN_LOGIN_MAX_FAILURES = 5;
    private static final int MAX_REQUEST_BODY_BYTES = 16 * 1024;
    private static final int MAX_MENU_NAME_LENGTH = 120;
    private static final int MAX_REASON_LENGTH = 1000;
    private static final int MAX_RECOMMENDED_MENU_LENGTH = 120;
    private static final int MAX_INQUIRY_MESSAGE_LENGTH = 2000;
    private static final int MAX_USER_KEY_LENGTH = 128;
    private static final String USER_ID_HEADER = "X-MenuPick-User-Id";
    private static final ZoneId MONTH_LIMIT_ZONE = ZoneId.of("Asia/Seoul");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Set<String> ALLOWED_CORS_ORIGINS = loadAllowedCorsOrigins();

    private final HttpServer server;
    private final DatabaseRepository database;
    private final Map<String, Instant> adminSessions = new ConcurrentHashMap<>();
    private final Map<String, LoginAttempt> adminLoginAttempts = new ConcurrentHashMap<>();
    private final String adminIdHash;

    public MenuPickServer(int port, String dbUrl, String dbUser, String dbPassword) throws IOException {
        this(port, dbUrl, dbUser, dbPassword, loadAdminIdHash());
    }

    public MenuPickServer(
            int port,
            String dbUrl,
            String dbUser,
            String dbPassword,
            String adminIdHash
    ) throws IOException {
        this(port, new MySqlDatabase(dbUrl, dbUser, dbPassword), adminIdHash);
    }

    MenuPickServer(int port, DatabaseRepository database, String adminIdHash) throws IOException {
        this.database = database;
        this.adminIdHash = trim(adminIdHash).toLowerCase(Locale.ROOT);
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.createContext("/health", new HealthHandler());
        this.server.createContext("/api/recommendations/latest", new LatestRecommendationHandler());
        this.server.createContext("/api/recommendations", new RecommendationHandler());
        this.server.createContext("/api/inquiries/latest", new LatestInquiryHandler());
        this.server.createContext("/api/inquiries", new InquiryHandler());
        this.server.createContext("/api/admin/login", new AdminLoginHandler());
        this.server.createContext("/api/admin/recommendations", new AdminRecommendationsHandler());
        this.server.createContext("/api/admin/inquiries", new AdminInquiriesHandler());
        this.server.createContext("/api/admin/decision", new AdminDecisionHandler());
        this.server.setExecutor(Executors.newFixedThreadPool(4));
    }

    public static void main(String[] args) throws Exception {
        int port = 8080;
        String dbUrl = getenvOrDefault("DB_URL", DEFAULT_DB_URL);
        String dbUser = getenvOrDefault("DB_USER", "root");
        String dbPassword = getenvOrDefault("DB_PASSWORD", "");

        for (String arg : args) {
            if (arg.startsWith("--port=")) {
                port = Integer.parseInt(arg.substring("--port=".length()));
            } else if (arg.startsWith("--db-url=")) {
                dbUrl = arg.substring("--db-url=".length());
            } else if (arg.startsWith("--db-user=")) {
                dbUser = arg.substring("--db-user=".length());
            } else if (arg.startsWith("--db-password=")) {
                throw new IllegalArgumentException("--db-password is not supported. Use the DB_PASSWORD environment variable.");
            }
        }

        MenuPickServer app = new MenuPickServer(port, dbUrl, dbUser, dbPassword);
        app.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> app.stop(0)));
        System.out.println("MenuPick Java server started on http://localhost:" + app.getPort());
        new CountDownLatch(1).await();
    }

    public void start() {
        this.server.start();
    }

    public void stop(int delaySeconds) {
        this.server.stop(delaySeconds);
        try {
            this.database.close();
        } catch (IOException ignored) {
        }
    }

    public int getPort() {
        return this.server.getAddress().getPort();
    }

    private final class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (isOptions(exchange)) {
                sendNoContent(exchange);
                return;
            }
            if (!isGet(exchange)) {
                sendMethodNotAllowed(exchange, "GET, OPTIONS");
                return;
            }
            sendJson(exchange, 200, Map.of(
                    "status", "ok",
                    "version", SERVER_VERSION
            ));
        }
    }

    private final class RecommendationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (isOptions(exchange)) {
                sendNoContent(exchange);
                return;
            }
            if (!isPost(exchange)) {
                sendMethodNotAllowed(exchange, "POST, OPTIONS");
                return;
            }

            try {
                Map<String, String> payload = JsonUtil.parseFlatObject(readBody(exchange));
                String menuName = trim(payload.get("menuName"));
                String reason = trim(payload.get("reason"));
                String recommendedMenu = trim(payload.get("recommendedMenu"));
                validateMaxLength("menuName", menuName, MAX_MENU_NAME_LENGTH);
                validateMaxLength("reason", reason, MAX_REASON_LENGTH);
                validateMaxLength("recommendedMenu", recommendedMenu, MAX_RECOMMENDED_MENU_LENGTH);

                if (recommendedMenu.isEmpty()) {
                    recommendedMenu = buildRecommendation(menuName, reason);
                }

                String userKey = requireUserKey(exchange);
                String monthKey = YearMonth.now(MONTH_LIMIT_ZONE).toString();
                RecommendationRecord record = database.saveMonthlyRecommendation(
                        userKey,
                        monthKey,
                        menuName.isEmpty() ? "메뉴명 미입력" : menuName,
                        reason.isEmpty() ? "이유 미입력" : reason,
                        recommendedMenu
                );
                if (record == null) {
                    sendJson(exchange, 429, error(
                            "monthly_limit_exceeded",
                            "메뉴 추천은 한 달에 한 번만 올릴 수 있습니다."
                    ));
                    return;
                }
                sendJson(exchange, 201, record.toMap());
            } catch (RequestTooLargeException ex) {
                sendJson(exchange, 413, error("request_too_large", ex.getMessage()));
            } catch (IllegalArgumentException ex) {
                sendJson(exchange, 400, error("invalid_request", ex.getMessage()));
            } catch (IOException ex) {
                sendJson(exchange, 500, error("db_error", "Database operation failed."));
            }
        }
    }

    private final class LatestRecommendationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (isOptions(exchange)) {
                sendNoContent(exchange);
                return;
            }
            if (!isGet(exchange)) {
                sendMethodNotAllowed(exchange, "GET, OPTIONS");
                return;
            }

            // 정책:
            // 1) 관리자가 아직 '공개 시각'을 설정하지 않았다면(결정 설정 없음) → 기존 방식대로 최신 추천을 보여줌
            // 2) 결정 설정이 존재하면 → 공개 시각 전에는 not_published, 공개 시각 이후에는 결정 메뉴만 보여줌
            try {
                MenuDecisionSetting setting = database.getMenuDecisionSetting();
                if (setting == null) {
                    sendJson(exchange, 404, Map.of(
                            "error", "not_published",
                            "message", "관리자가 아직 결정 메뉴를 공개하지 않았습니다."
                    ));
                    return;
                }

                // 결정 설정이 DB에 남아있더라도, 결정 설정 이후에 새로운 추천이 들어오면
                // 다시 '결정 중' 상태로 돌아가야 합니다.
                // (예: 이전 테스트에서 남은 결정 설정이 현재 추천 플로우를 방해하는 경우)
                RecommendationRecord latest = database.getLatestRecommendation();
                if (latest != null) {
                    try {
                        Instant latestCreatedAt = Instant.parse(trim(latest.createdAt()));
                        Instant decisionUpdatedAt = Instant.parse(trim(setting.updatedAt()));
                        if (latestCreatedAt.isAfter(decisionUpdatedAt)) {
                            sendJson(exchange, 404, Map.of(
                                    "error", "not_published",
                                    "message", "관리자가 아직 결정 메뉴를 공개하지 않았습니다."
                            ));
                            return;
                        }
                    } catch (Exception ignored) {
                        // 파싱 실패 시 기존 로직 유지
                    }
                }

                Instant publishAt;
                try {
                    publishAt = Instant.parse(setting.publishAt());
                } catch (Exception ex) {
                    sendJson(exchange, 500, error("server_error", "Invalid publishAt format."));
                    return;
                }

                if (Instant.now().isBefore(publishAt)) {
                    sendJson(exchange, 404, Map.of(
                            "error", "not_published",
                            "message", "아직 공개 시간이 아닙니다.",
                            "publishAt", setting.publishAt()
                    ));
                    return;
                }

                RecommendationRecord record = database.getRecommendationById(setting.recommendationId());
                if (record == null) {
                    sendJson(exchange, 404, error("not_found", "결정 메뉴가 존재하지 않습니다."));
                    return;
                }

                sendJson(exchange, 200, record.toMap());
            } catch (IOException ex) {
                sendJson(exchange, 500, error("db_error", "Database operation failed."));
            }
        }
    }

    private final class AdminDecisionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (isOptions(exchange)) {
                sendNoContent(exchange);
                return;
            }

            if (!isAdminAuthorized(exchange)) {
                sendJson(exchange, 401, error("unauthorized", "Admin token is missing or expired."));
                return;
            }

            if (isGet(exchange)) {
                try {
                    MenuDecisionSetting setting = database.getMenuDecisionSetting();
                    if (setting == null) {
                        sendJson(exchange, 200, Map.of("enabled", false));
                        return;
                    }
                    sendJson(exchange, 200, Map.of(
                            "enabled", true,
                            "setting", setting.toMap()
                    ));
                } catch (IOException ex) {
                    sendJson(exchange, 500, error("db_error", "Database operation failed."));
                }
                return;
            }

            if (!isPost(exchange)) {
                sendMethodNotAllowed(exchange, "GET, POST, OPTIONS");
                return;
            }

            try {
                Map<String, String> payload = JsonUtil.parseFlatObject(readBody(exchange));
                long recommendationId;
                try {
                    recommendationId = Long.parseLong(trim(payload.get("recommendationId")));
                } catch (NumberFormatException ex) {
                    sendJson(exchange, 400, error("validation_error", "recommendationId is required"));
                    return;
                }

                String publishAt = trim(payload.get("publishAt"));
                if (publishAt.isEmpty()) {
                    sendJson(exchange, 400, error("validation_error", "publishAt is required (ISO-8601)"));
                    return;
                }

                // recommendation 존재 확인
                RecommendationRecord record = database.getRecommendationById(recommendationId);
                if (record == null) {
                    sendJson(exchange, 404, error("not_found", "recommendation not found"));
                    return;
                }

                // publishAt 파싱 검증
                try {
                    Instant.parse(publishAt);
                } catch (Exception ex) {
                    sendJson(exchange, 400, error("validation_error", "publishAt must be ISO-8601 (e.g. 2026-04-24T12:00:00Z)"));
                    return;
                }

                database.upsertMenuDecisionSetting(recommendationId, publishAt);
                sendJson(exchange, 200, Map.of(
                        "status", "ok",
                        "recommendationId", recommendationId,
                        "publishAt", publishAt
                ));
            } catch (RequestTooLargeException ex) {
                sendJson(exchange, 413, error("request_too_large", ex.getMessage()));
            } catch (IllegalArgumentException ex) {
                sendJson(exchange, 400, error("invalid_request", ex.getMessage()));
            } catch (IOException ex) {
                sendJson(exchange, 500, error("db_error", "Database operation failed."));
            }
        }
    }

    private final class InquiryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (isOptions(exchange)) {
                sendNoContent(exchange);
                return;
            }
            if (!isPost(exchange)) {
                sendMethodNotAllowed(exchange, "POST, OPTIONS");
                return;
            }

            try {
                Map<String, String> payload = JsonUtil.parseFlatObject(readBody(exchange));
                String message = trim(payload.get("message"));
                if (message.isEmpty()) {
                    sendJson(exchange, 400, error("validation_error", "message is required"));
                    return;
                }
                validateMaxLength("message", message, MAX_INQUIRY_MESSAGE_LENGTH);

                InquiryRecord record = database.saveInquiry(message, ADMIN_EMAIL);
                sendJson(exchange, 201, record.toMap());
            } catch (RequestTooLargeException ex) {
                sendJson(exchange, 413, error("request_too_large", ex.getMessage()));
            } catch (IllegalArgumentException ex) {
                sendJson(exchange, 400, error("invalid_request", ex.getMessage()));
            } catch (IOException ex) {
                sendJson(exchange, 500, error("db_error", "Database operation failed."));
            }
        }
    }

    private final class LatestInquiryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (isOptions(exchange)) {
                sendNoContent(exchange);
                return;
            }
            if (!isGet(exchange)) {
                sendMethodNotAllowed(exchange, "GET, OPTIONS");
                return;
            }
            if (!isAdminAuthorized(exchange)) {
                sendJson(exchange, 401, error("unauthorized", "Admin token is missing or expired."));
                return;
            }

            try {
                InquiryRecord record = database.getLatestInquiry();
                if (record == null) {
                    sendJson(exchange, 404, error("not_found", "No inquiry found."));
                    return;
                }
                sendJson(exchange, 200, record.toMap());
            } catch (IOException ex) {
                sendJson(exchange, 500, error("db_error", "Database operation failed."));
            }
        }
    }

    private final class AdminLoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (isOptions(exchange)) {
                sendNoContent(exchange);
                return;
            }
            if (!isPost(exchange)) {
                sendMethodNotAllowed(exchange, "POST, OPTIONS");
                return;
            }

            try {
                String clientKey = loginClientKey(exchange);
                if (isLoginRateLimited(clientKey)) {
                    sendJson(exchange, 429, error("rate_limited", "Too many failed login attempts. Try again later."));
                    return;
                }

                Map<String, String> payload = JsonUtil.parseFlatObject(readBody(exchange));
                String id = trim(payload.get("id"));
                validateMaxLength("id", id, MAX_MENU_NAME_LENGTH);

                if (id.isEmpty() || !sha256Hex(id).equals(adminIdHash)) {
                    recordLoginFailure(clientKey);
                    sendJson(exchange, 401, error("unauthorized", "Invalid admin credentials."));
                    return;
                }

                clearLoginFailures(clientKey);
                String token = generateAdminToken();
                Instant expiresAt = Instant.now().plus(ADMIN_TOKEN_TTL);
                adminSessions.put(token, expiresAt);

                sendJson(exchange, 200, Map.of(
                        "token", token,
                        "expiresAt", expiresAt.toString(),
                        "adminEmail", ADMIN_EMAIL
                ));
            } catch (RequestTooLargeException ex) {
                sendJson(exchange, 413, error("request_too_large", ex.getMessage()));
            } catch (IllegalArgumentException ex) {
                sendJson(exchange, 400, error("invalid_request", ex.getMessage()));
            }
        }
    }

    private final class AdminRecommendationsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (isOptions(exchange)) {
                sendNoContent(exchange);
                return;
            }
            if (!isAdminAuthorized(exchange)) {
                sendJson(exchange, 401, error("unauthorized", "Admin token is missing or expired."));
                return;
            }

            if (isPost(exchange) || isDelete(exchange)) {
                try {
                    List<Long> ids;
                    if (isDelete(exchange)) {
                        // DELETE는 body가 비어있을 수 있어 query도 허용
                        ids = parseIdsFromQuery(exchange);
                        if (ids.isEmpty()) {
                            ids = parseIdsFromBody(readBody(exchange));
                        }
                    } else {
                        ids = parseIdsFromBody(readBody(exchange));
                    }
                    if (ids.isEmpty()) {
                        sendJson(exchange, 400, error("validation_error", "ids is required"));
                        return;
                    }

                    int deleted = database.deleteRecommendations(ids);
                    sendJson(exchange, 200, Map.of(
                            "status", "ok",
                            "requested", ids.size(),
                            "deleted", deleted
                    ));
                } catch (RequestTooLargeException ex) {
                    sendJson(exchange, 413, error("request_too_large", ex.getMessage()));
                } catch (IllegalArgumentException ex) {
                    sendJson(exchange, 400, error("invalid_request", ex.getMessage()));
                } catch (IOException ex) {
                    sendJson(exchange, 500, error("db_error", "Database operation failed."));
                }
                return;
            }

            if (!isGet(exchange)) {
                sendMethodNotAllowed(exchange, "GET, POST, DELETE, OPTIONS");
                return;
            }

            try {
                int limit = parseLimit(exchange);
                List<RecommendationRecord> records = database.listRecommendations(limit);
                List<Map<String, Object>> items = new ArrayList<>();
                for (RecommendationRecord record : records) {
                    items.add(record.toMap());
                }
                sendJson(exchange, 200, Map.of("items", items, "count", items.size()));
            } catch (IOException ex) {
                sendJson(exchange, 500, error("db_error", "Database operation failed."));
            }
        }
    }

    /**
     * 요청 body에서 ids를 파싱합니다.
     *
     * 허용 포맷:
     *  - {"ids":[1,2,3]}
     *  - {"ids":"1,2,3"}
     */
    private static List<Long> parseIdsFromBody(String rawBody) {
        String body = rawBody == null ? "" : rawBody.trim();
        if (body.isEmpty()) {
            throw new IllegalArgumentException("JSON body is empty");
        }

        int keyIndex = body.indexOf("\"ids\"");
        if (keyIndex < 0) {
            throw new IllegalArgumentException("ids is required");
        }

        int colonIndex = body.indexOf(':', keyIndex);
        if (colonIndex < 0) {
            throw new IllegalArgumentException("Invalid JSON: ':' after ids is missing");
        }

        int i = colonIndex + 1;
        while (i < body.length() && Character.isWhitespace(body.charAt(i))) {
            i++;
        }
        if (i >= body.length()) {
            throw new IllegalArgumentException("Invalid JSON: ids value is missing");
        }

        char first = body.charAt(i);
        List<Long> ids = new ArrayList<>();

        if (first == '[') {
            i++;
            StringBuilder token = new StringBuilder();
            boolean closed = false;
            while (i < body.length()) {
                char ch = body.charAt(i);
                if (ch == ']') {
                    addIdToken(ids, token.toString());
                    closed = true;
                    break;
                }
                if (ch == ',') {
                    addIdToken(ids, token.toString());
                    token.setLength(0);
                } else {
                    token.append(ch);
                }
                i++;
            }
            if (!closed) {
                throw new IllegalArgumentException("Invalid JSON: ids array is not closed");
            }
            return ids;
        }

        if (first == '"') {
            int end = body.indexOf('"', i + 1);
            if (end < 0) {
                throw new IllegalArgumentException("Invalid JSON: unterminated string");
            }
            String csv = body.substring(i + 1, end);
            for (String part : csv.split(",")) {
                addIdToken(ids, part);
            }
            return ids;
        }

        // unquoted primitive
        int end = i;
        while (end < body.length()) {
            char ch = body.charAt(end);
            if (ch == ',' || ch == '}') {
                break;
            }
            end++;
        }
        String csv = body.substring(i, end);
        for (String part : csv.split(",")) {
            addIdToken(ids, part);
        }
        return ids;
    }

    private static void addIdToken(List<Long> ids, String raw) {
        String token = trim(raw);
        if (token.isEmpty()) {
            return;
        }
        if (token.startsWith("\"") && token.endsWith("\"") && token.length() >= 2) {
            token = token.substring(1, token.length() - 1).trim();
        }
        if (token.isEmpty()) {
            return;
        }
        try {
            long id = Long.parseLong(token);
            if (id <= 0) {
                throw new IllegalArgumentException("id must be positive");
            }
            ids.add(id);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid id: " + token);
        }
    }

    private final class AdminInquiriesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (isOptions(exchange)) {
                sendNoContent(exchange);
                return;
            }
            if (!isGet(exchange)) {
                sendMethodNotAllowed(exchange, "GET, OPTIONS");
                return;
            }
            if (!isAdminAuthorized(exchange)) {
                sendJson(exchange, 401, error("unauthorized", "Admin token is missing or expired."));
                return;
            }

            try {
                int limit = parseLimit(exchange);
                List<InquiryRecord> records = database.listInquiries(limit);
                List<Map<String, Object>> items = new ArrayList<>();
                for (InquiryRecord record : records) {
                    items.add(record.toMap());
                }
                sendJson(exchange, 200, Map.of("items", items, "count", items.size()));
            } catch (IOException ex) {
                sendJson(exchange, 500, error("db_error", "Database operation failed."));
            }
        }
    }

    private static boolean isGet(HttpExchange exchange) {
        return "GET".equalsIgnoreCase(exchange.getRequestMethod());
    }

    private static boolean isPost(HttpExchange exchange) {
        return "POST".equalsIgnoreCase(exchange.getRequestMethod());
    }

    private static boolean isDelete(HttpExchange exchange) {
        return "DELETE".equalsIgnoreCase(exchange.getRequestMethod());
    }

    private static boolean isOptions(HttpExchange exchange) {
        return "OPTIONS".equalsIgnoreCase(exchange.getRequestMethod());
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream inputStream = exchange.getRequestBody()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            StringBuilder builder = new StringBuilder();
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                total += read;
                if (total > MAX_REQUEST_BODY_BYTES) {
                    throw new RequestTooLargeException("Request body must be " + MAX_REQUEST_BODY_BYTES + " bytes or less.");
                }
                builder.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
            }
            return builder.toString().trim();
        }
    }

    private boolean isAdminAuthorized(HttpExchange exchange) {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return false;
        }

        String token = auth.substring("Bearer ".length()).trim();
        Instant expiresAt = adminSessions.get(token);
        if (expiresAt == null) {
            return false;
        }
        if (Instant.now().isAfter(expiresAt)) {
            adminSessions.remove(token);
            return false;
        }
        return true;
    }

    private int parseLimit(HttpExchange exchange) {
        String rawQuery = exchange.getRequestURI().getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return 50;
        }
        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && "limit".equals(parts[0])) {
                try {
                    return Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {
                    return 50;
                }
            }
        }
        return 50;
    }

    private static void sendJson(HttpExchange exchange, int statusCode, Map<String, ?> body) throws IOException {
        byte[] bytes = JsonUtil.stringify(body).getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        applyCors(exchange, headers);
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("X-MenuPick-Server-Version", SERVER_VERSION);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private static void sendNoContent(HttpExchange exchange) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        applyCors(exchange, headers);
        headers.set("X-MenuPick-Server-Version", SERVER_VERSION);
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private static void sendMethodNotAllowed(HttpExchange exchange, String allow) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        applyCors(exchange, headers);
        headers.set("Allow", allow);
        sendJson(exchange, 405, error("method_not_allowed", "Only " + allow + " are allowed."));
    }

    private static void applyCors(HttpExchange exchange, Headers headers) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && isAllowedCorsOrigin(origin)) {
            headers.set("Access-Control-Allow-Origin", origin);
            headers.set("Vary", "Origin");
        }
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        headers.set("Access-Control-Allow-Methods", "GET,POST,DELETE,OPTIONS");
    }

    private static boolean isAllowedCorsOrigin(String origin) {
        if (ALLOWED_CORS_ORIGINS.contains(origin)) {
            return true;
        }
        URI uri;
        try {
            uri = URI.create(origin);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return ALLOWED_CORS_ORIGINS.contains("http://localhost:*") &&
                ("localhost".equals(normalizedHost) || "127.0.0.1".equals(normalizedHost) || "::1".equals(normalizedHost)) &&
                ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
    }

    private static Set<String> loadAllowedCorsOrigins() {
        String raw = System.getenv("CORS_ALLOWED_ORIGINS");
        if (raw == null || raw.isBlank()) {
            return Set.of("http://localhost:*");
        }
        Set<String> origins = new HashSet<>();
        for (String part : raw.split(",")) {
            String origin = part.trim();
            if (!origin.isEmpty() && !"*".equals(origin)) {
                origins.add(origin);
            }
        }
        return origins.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(origins);
    }

    private static List<Long> parseIdsFromQuery(HttpExchange exchange) {
        String rawQuery = exchange.getRequestURI().getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return List.of();
        }
        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && "ids".equals(parts[0])) {
                List<Long> ids = new ArrayList<>();
                for (String part : parts[1].split(",")) {
                    addIdToken(ids, part);
                }
                return ids;
            }
        }
        return List.of();
    }

    private static Map<String, Object> error(String code, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", code);
        error.put("message", message);
        return error;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String getenvOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private boolean isLoginRateLimited(String clientKey) {
        LoginAttempt attempt = adminLoginAttempts.get(clientKey);
        if (attempt == null) {
            return false;
        }
        Instant now = Instant.now();
        if (now.isAfter(attempt.windowStartedAt.plus(ADMIN_LOGIN_WINDOW))) {
            adminLoginAttempts.remove(clientKey);
            return false;
        }
        return attempt.failures >= ADMIN_LOGIN_MAX_FAILURES;
    }

    private void recordLoginFailure(String clientKey) {
        Instant now = Instant.now();
        adminLoginAttempts.compute(clientKey, (key, current) -> {
            if (current == null || now.isAfter(current.windowStartedAt.plus(ADMIN_LOGIN_WINDOW))) {
                return new LoginAttempt(now, 1);
            }
            return new LoginAttempt(current.windowStartedAt, current.failures + 1);
        });
    }

    private void clearLoginFailures(String clientKey) {
        adminLoginAttempts.remove(clientKey);
    }

    private static String loginClientKey(HttpExchange exchange) {
        InetSocketAddress address = exchange.getRemoteAddress();
        if (address == null || address.getAddress() == null) {
            return "unknown";
        }
        return address.getAddress().getHostAddress();
    }

    private static String requireUserKey(HttpExchange exchange) {
        String userKey = trim(exchange.getRequestHeaders().getFirst(USER_ID_HEADER));
        if (userKey.isEmpty()) {
            throw new IllegalArgumentException(USER_ID_HEADER + " header is required.");
        }
        validateMaxLength(USER_ID_HEADER, userKey, MAX_USER_KEY_LENGTH);
        for (int i = 0; i < userKey.length(); i++) {
            char ch = userKey.charAt(i);
            boolean allowed = (ch >= 'a' && ch <= 'z') ||
                    (ch >= 'A' && ch <= 'Z') ||
                    (ch >= '0' && ch <= '9') ||
                    ch == '_' ||
                    ch == '-';
            if (!allowed) {
                throw new IllegalArgumentException(USER_ID_HEADER + " contains invalid characters.");
            }
        }
        return userKey;
    }

    private static String generateAdminToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void validateMaxLength(String field, String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be " + maxLength + " characters or less.");
        }
    }

    private static String loadAdminIdHash() {
        String envHash = System.getenv("ADMIN_ID_HASH");
        if (envHash != null && !envHash.isBlank()) {
            return envHash;
        }

        Path hashFile = Path.of(getenvOrDefault("ADMIN_ID_HASH_FILE", DEFAULT_ADMIN_ID_HASH_FILE));
        if (!Files.exists(hashFile)) {
            throw new IllegalStateException(
                    "ADMIN_ID_HASH 또는 ADMIN_ID_HASH_FILE이 필요합니다. 현재 파일 없음: " + hashFile.toAbsolutePath()
            );
        }

        try {
            return Files.readString(hashFile, StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            throw new IllegalStateException("관리자 해시 파일 읽기 실패: " + ex.getMessage(), ex);
        }
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 지원 불가", ex);
        }
    }

    private static String buildRecommendation(String menuName, String reason) {
        String source = (reason == null ? "" : reason).toLowerCase(Locale.ROOT);
        if (!menuName.isBlank()) {
            return menuName;
        }
        if (source.contains("매운")) {
            return "떡볶이";
        }
        if (source.contains("가벼")) {
            return "샐러드";
        }
        if (source.contains("든든") || source.contains("배고")) {
            return "제육덮밥";
        }
        if (source.contains("국물")) {
            return "라면";
        }
        String[] fallbacks = {"김치볶음밥", "비빔밥", "파스타"};
        return fallbacks[Math.abs(source.length()) % fallbacks.length];
    }

    private record LoginAttempt(Instant windowStartedAt, int failures) {
    }

    private static final class RequestTooLargeException extends IOException {
        private RequestTooLargeException(String message) {
            super(message);
        }
    }
}
