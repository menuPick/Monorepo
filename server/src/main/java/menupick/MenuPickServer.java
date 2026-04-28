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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

public final class MenuPickServer {
    public static final String ADMIN_EMAIL = "junsumon090608@dgsw.hs.kr";
    public static final String DEFAULT_DB_URL = "jdbc:mysql://localhost:3306/menupick?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&createDatabaseIfNotExist=true";
    public static final String DEFAULT_ADMIN_ID_HASH_FILE = "secrets/admin_id_hash.txt";
    private static final String SERVER_VERSION = "2026-04-27";
    private static final Duration ADMIN_TOKEN_TTL = Duration.ofHours(2);

    private final HttpServer server;
    private final DatabaseRepository database;
    private final Map<String, Instant> adminSessions = new ConcurrentHashMap<>();
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
        this.database = new MySqlDatabase(dbUrl, dbUser, dbPassword);
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
                dbPassword = arg.substring("--db-password=".length());
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

                if (recommendedMenu.isEmpty()) {
                    recommendedMenu = buildRecommendation(menuName, reason);
                }

                RecommendationRecord record = database.saveRecommendation(
                        menuName.isEmpty() ? "메뉴명 미입력" : menuName,
                        reason.isEmpty() ? "이유 미입력" : reason,
                        recommendedMenu
                );
                sendJson(exchange, 201, record.toMap());
            } catch (IllegalArgumentException ex) {
                sendJson(exchange, 400, error("invalid_request", ex.getMessage()));
            } catch (IOException ex) {
                sendJson(exchange, 500, error("db_error", ex.getMessage()));
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
                sendJson(exchange, 500, error("db_error", ex.getMessage()));
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
                    sendJson(exchange, 500, error("db_error", ex.getMessage()));
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
            } catch (IllegalArgumentException ex) {
                sendJson(exchange, 400, error("invalid_request", ex.getMessage()));
            } catch (IOException ex) {
                sendJson(exchange, 500, error("db_error", ex.getMessage()));
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

                InquiryRecord record = database.saveInquiry(message, ADMIN_EMAIL);
                sendJson(exchange, 201, record.toMap());
            } catch (IllegalArgumentException ex) {
                sendJson(exchange, 400, error("invalid_request", ex.getMessage()));
            } catch (IOException ex) {
                sendJson(exchange, 500, error("db_error", ex.getMessage()));
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

            try {
                InquiryRecord record = database.getLatestInquiry();
                if (record == null) {
                    sendJson(exchange, 404, error("not_found", "No inquiry found."));
                    return;
                }
                sendJson(exchange, 200, record.toMap());
            } catch (IOException ex) {
                sendJson(exchange, 500, error("db_error", ex.getMessage()));
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
                Map<String, String> payload = JsonUtil.parseFlatObject(readBody(exchange));
                String id = trim(payload.get("id"));

                if (id.isEmpty() || !sha256Hex(id).equals(adminIdHash)) {
                    sendJson(exchange, 401, error("unauthorized", "Invalid admin credentials."));
                    return;
                }

                String token = UUID.randomUUID().toString();
                Instant expiresAt = Instant.now().plus(ADMIN_TOKEN_TTL);
                adminSessions.put(token, expiresAt);

                sendJson(exchange, 200, Map.of(
                        "token", token,
                        "expiresAt", expiresAt.toString(),
                        "adminEmail", ADMIN_EMAIL
                ));
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
                } catch (IllegalArgumentException ex) {
                    sendJson(exchange, 400, error("invalid_request", ex.getMessage()));
                } catch (IOException ex) {
                    sendJson(exchange, 500, error("db_error", ex.getMessage()));
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
                sendJson(exchange, 500, error("db_error", ex.getMessage()));
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
                sendJson(exchange, 500, error("db_error", ex.getMessage()));
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
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
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
        applyCors(headers);
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("X-MenuPick-Server-Version", SERVER_VERSION);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private static void sendNoContent(HttpExchange exchange) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        applyCors(headers);
        headers.set("X-MenuPick-Server-Version", SERVER_VERSION);
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private static void sendMethodNotAllowed(HttpExchange exchange, String allow) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        applyCors(headers);
        headers.set("Allow", allow);
        sendJson(exchange, 405, error("method_not_allowed", "Only " + allow + " are allowed."));
    }

    private static void applyCors(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        headers.set("Access-Control-Allow-Methods", "GET,POST,DELETE,OPTIONS");
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
}

