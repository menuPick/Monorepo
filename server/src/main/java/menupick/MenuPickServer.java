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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class MenuPickServer {
    public static final String DEFAULT_DB_URL =
            "jdbc:mysql://localhost:3306/menupick?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&createDatabaseIfNotExist=true";
    public static final String ADMIN_EMAIL = "admin@local";

    private static final Logger logger = Logger.getLogger(MenuPickServer.class.getName());
    private static final int DEFAULT_PORT = 80;
    private static final int MAX_BODY_BYTES = 16 * 1024;
    private static final int ADMIN_TOKEN_BYTES = 32;
    private static final long ADMIN_SESSION_SECONDS = 60L * 60L * 8L;
    private static final int LOGIN_FAIL_LIMIT = 5;
    private static final long LOGIN_FAIL_WINDOW_SECONDS = 60L * 5L;
    private static final int RECOMMENDATION_WRITE_LIMIT = 3;
    private static final int INQUIRY_WRITE_LIMIT = 5;
    private static final long PUBLIC_WRITE_WINDOW_SECONDS = 60L * 10L;

    private final int requestedPort;
    private final DatabaseRepository database;
    private final AdminService adminService;
    private final PublicWriteGuard publicWriteGuard;
    private final CorsPolicy corsPolicy;
    private HttpServer httpServer;

    public MenuPickServer(int port, DatabaseRepository database, String adminIdHash) {
        this.requestedPort = port;
        this.database = database;
        this.adminService = new AdminService(adminIdHash);
        this.publicWriteGuard = new PublicWriteGuard();
        this.corsPolicy = CorsPolicy.fromEnvironment();
    }

    public MenuPickServer(int port, String dbUrl, String dbUser, String dbPassword, String adminIdHash) throws IOException {
        this(port, new MySqlDatabase(dbUrl, dbUser, dbPassword), adminIdHash);
    }

    public static void main(String[] args) {
        int port = parsePort(args, envOrDefault("PORT", String.valueOf(DEFAULT_PORT)));
        String dbUrl = envOrDefault("DB_URL", DEFAULT_DB_URL);
        String dbUser = envOrDefault("DB_USER", "root");
        String dbPassword = envOrDefault("DB_PASSWORD", "");
        String adminIdHash = loadAdminIdHash();

        if (adminIdHash.isBlank()) {
            logger.severe("ADMIN_ID_HASH or ADMIN_ID_HASH_FILE is required.");
            return;
        }

        try {
            MenuPickServer server = new MenuPickServer(port, dbUrl, dbUser, dbPassword, adminIdHash);
            server.start();
            logger.info("MenuPick Server started on port " + server.getPort());
            logger.info("Health check available at http://localhost:" + server.getPort() + "/health");
        } catch (IOException e) {
            logger.severe("Server failed to start: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(requestedPort), 0);
        httpServer.createContext("/api/recommendations", new CorsMiddleware(corsPolicy, new RecommendationHandler(database, publicWriteGuard)));
        httpServer.createContext("/api/inquiries", new CorsMiddleware(corsPolicy, new InquiryHandler(database, adminService, publicWriteGuard)));
        httpServer.createContext("/api/admin", new CorsMiddleware(corsPolicy, new AdminHandler(database, adminService)));
        httpServer.createContext("/health", new CorsMiddleware(corsPolicy, new HealthHandler()));
        httpServer.setExecutor(Executors.newFixedThreadPool(10));
        httpServer.start();
    }

    public int getPort() {
        if (httpServer == null) {
            return requestedPort;
        }
        return httpServer.getAddress().getPort();
    }

    public void stop(int delaySeconds) {
        if (httpServer != null) {
            httpServer.stop(delaySeconds);
        }
        try {
            database.close();
        } catch (IOException ignored) {
        }
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static int parsePort(String[] args, String fallback) {
        String raw = fallback;
        for (String arg : args) {
            if (arg.startsWith("--port=")) {
                raw = arg.substring("--port=".length());
            }
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            logger.warning("Invalid PORT value: " + raw + ". Using default: " + DEFAULT_PORT);
            return DEFAULT_PORT;
        }
    }

    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String loadAdminIdHash() {
        String hash = System.getenv("ADMIN_ID_HASH");
        if (hash != null && !hash.isBlank()) {
            return hash.trim();
        }

        String file = System.getenv("ADMIN_ID_HASH_FILE");
        if (file == null || file.isBlank()) {
            return "";
        }

        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(file), StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            logger.severe("Failed to read ADMIN_ID_HASH_FILE: " + ex.getMessage());
            return "";
        }
    }

    private static final class CorsPolicy {
        private final Set<String> allowedOrigins;

        private CorsPolicy(Set<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        static CorsPolicy fromEnvironment() {
            Set<String> origins = new HashSet<>(List.of(
                    "http://localhost:3000",
                    "http://localhost:5173",
                    "http://localhost:8080",
                    "http://localhost:18080",
                    "http://127.0.0.1:3000",
                    "http://127.0.0.1:5173",
                    "http://127.0.0.1:8080",
                    "http://127.0.0.1:18080"
            ));
            String extra = System.getenv("ALLOWED_ORIGINS");
            if (extra != null && !extra.isBlank()) {
                Arrays.stream(extra.split(","))
                        .map(String::trim)
                        .filter(origin -> !origin.isEmpty())
                        .forEach(origins::add);
            }
            return new CorsPolicy(origins);
        }

        boolean isAllowed(String origin) {
            return origin != null && allowedOrigins.contains(origin);
        }
    }

    public static class CorsMiddleware implements HttpHandler {
        private final CorsPolicy policy;
        private final HttpHandler next;

        public CorsMiddleware(CorsPolicy policy, HttpHandler next) {
            this.policy = policy;
            this.next = next;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            if (policy.isAllowed(origin)) {
                Headers h = exchange.getResponseHeaders();
                h.set("Access-Control-Allow-Origin", origin);
                h.set("Vary", "Origin");
                h.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS, DELETE");
                h.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
                h.set("Access-Control-Max-Age", "600");
            }

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            next.handle(exchange);
        }
    }

    private abstract static class BaseHandler implements HttpHandler {
        protected void sendJson(HttpExchange exchange, int status, Object data) throws IOException {
            String json = toJson(data);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        protected void sendError(HttpExchange exchange, int status, String error, String message) throws IOException {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", error);
            body.put("message", message);
            sendJson(exchange, status, body);
        }

        protected Map<String, String> readJsonBody(HttpExchange exchange) throws IOException {
            return JsonUtil.parseFlatObject(readBody(exchange));
        }

        protected String readBody(HttpExchange exchange) throws IOException {
            try (InputStream input = exchange.getRequestBody();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_BODY_BYTES) {
                        throw new PayloadTooLargeException();
                    }
                    output.write(buffer, 0, read);
                }
                return output.toString(StandardCharsets.UTF_8);
            }
        }

        protected boolean requireMethod(HttpExchange exchange, String... methods) throws IOException {
            for (String method : methods) {
                if (method.equalsIgnoreCase(exchange.getRequestMethod())) {
                    return true;
                }
            }
            sendError(exchange, 405, "method_not_allowed", "지원하지 않는 요청 방식입니다.");
            return false;
        }

        protected boolean requireAdmin(HttpExchange exchange, AdminService adminService) throws IOException {
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (!adminService.isValidBearer(authorization)) {
                sendError(exchange, 401, "unauthorized", "관리자 인증이 필요합니다.");
                return false;
            }
            return true;
        }

        protected void handleFailure(HttpExchange exchange, Exception ex) throws IOException {
            if (ex instanceof PayloadTooLargeException) {
                sendError(exchange, 413, "payload_too_large", "요청 본문이 너무 큽니다.");
                return;
            }
            if (ex instanceof IllegalArgumentException) {
                sendError(exchange, 400, "bad_request", ex.getMessage());
                return;
            }
            sendError(exchange, 500, "server_error", "서버 오류가 발생했습니다.");
        }

        private String toJson(Object data) {
            if (data instanceof Map<?, ?> map) {
                Map<String, Object> values = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    values.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                return JsonUtil.stringify(values);
            }
            if (data instanceof RecommendationRecord record) {
                return JsonUtil.stringify(record.toMap());
            }
            if (data instanceof InquiryRecord record) {
                return JsonUtil.stringify(record.toMap());
            }
            if (data instanceof List<?> list) {
                List<Map<String, Object>> maps = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof RecommendationRecord record) {
                        maps.add(record.toMap());
                    } else if (item instanceof InquiryRecord record) {
                        maps.add(record.toMap());
                    }
                }
                Map<String, Object> wrapper = new LinkedHashMap<>();
                wrapper.put("items", maps);
                return JsonUtil.stringify(wrapper);
            }
            return JsonUtil.stringify(Map.of("value", String.valueOf(data)));
        }
    }

    private static final class RecommendationHandler extends BaseHandler {
        private final DatabaseRepository database;
        private final PublicWriteGuard publicWriteGuard;

        private RecommendationHandler(DatabaseRepository database, PublicWriteGuard publicWriteGuard) {
            this.database = database;
            this.publicWriteGuard = publicWriteGuard;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String path = exchange.getRequestURI().getPath();
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) && path.equals("/api/recommendations")) {
                    String clientKey = clientKey(exchange);
                    if (!publicWriteGuard.allow("recommendations", clientKey, RECOMMENDATION_WRITE_LIMIT)) {
                        sendRateLimited(exchange);
                        return;
                    }
                    Map<String, String> body = readJsonBody(exchange);
                    String menuName = requiredText(body, "menuName", 255);
                    String reason = requiredText(body, "reason", 2000);
                    String recommendedMenu = cleanText(body.get("recommendedMenu"), 255);
                    if (recommendedMenu.isBlank()) {
                        recommendedMenu = recommendMenu(menuName, reason);
                    }

                    String userKey = "network:" + sha256Hex(clientKey);
                    String monthKey = YearMonth.now(ZoneOffset.UTC).toString();
                    RecommendationRecord record = database.saveMonthlyRecommendation(userKey, monthKey, menuName, reason, recommendedMenu);
                    if (record == null) {
                        sendError(exchange, 429, "monthly_limit_exceeded", "메뉴 추천은 한 달에 한 번만 올릴 수 있습니다.");
                        return;
                    }
                    sendJson(exchange, 201, record);
                    return;
                }

                if ("GET".equalsIgnoreCase(exchange.getRequestMethod()) && path.equals("/api/recommendations/latest")) {
                    RecommendationRecord latest = latestPublishedRecommendation();
                    if (latest == null) {
                        sendError(exchange, 404, "not_published", "결정 메뉴가 아직 공개되지 않았습니다.");
                        return;
                    }
                    sendJson(exchange, 200, latest);
                    return;
                }

                sendError(exchange, 404, "not_found", "요청한 API를 찾을 수 없습니다.");
            } catch (Exception ex) {
                handleFailure(exchange, ex);
            }
        }

        private RecommendationRecord latestPublishedRecommendation() throws IOException {
            MenuDecisionSetting decision = database.getMenuDecisionSetting();
            if (decision == null) {
                return database.getLatestRecommendation();
            }

            Instant publishAt = Instant.parse(decision.publishAt());
            if (Instant.now().isBefore(publishAt)) {
                return null;
            }
            return database.getRecommendationById(decision.recommendationId());
        }

        private void sendRateLimited(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Retry-After", String.valueOf(PUBLIC_WRITE_WINDOW_SECONDS));
            sendError(exchange, 429, "rate_limited", "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    private static final class InquiryHandler extends BaseHandler {
        private final DatabaseRepository database;
        private final AdminService adminService;
        private final PublicWriteGuard publicWriteGuard;

        private InquiryHandler(DatabaseRepository database, AdminService adminService, PublicWriteGuard publicWriteGuard) {
            this.database = database;
            this.adminService = adminService;
            this.publicWriteGuard = publicWriteGuard;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String path = exchange.getRequestURI().getPath();
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) && path.equals("/api/inquiries")) {
                    if (!publicWriteGuard.allow("inquiries", clientKey(exchange), INQUIRY_WRITE_LIMIT)) {
                        exchange.getResponseHeaders().set("Retry-After", String.valueOf(PUBLIC_WRITE_WINDOW_SECONDS));
                        sendError(exchange, 429, "rate_limited", "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
                        return;
                    }
                    Map<String, String> body = readJsonBody(exchange);
                    InquiryRecord record = database.saveInquiry(requiredText(body, "message", 4000), ADMIN_EMAIL);
                    sendJson(exchange, 201, record);
                    return;
                }

                if ("GET".equalsIgnoreCase(exchange.getRequestMethod()) && path.equals("/api/inquiries/latest")) {
                    if (!requireAdmin(exchange, adminService)) {
                        return;
                    }
                    InquiryRecord latest = database.getLatestInquiry();
                    if (latest == null) {
                        sendError(exchange, 404, "not_found", "문의가 없습니다.");
                        return;
                    }
                    sendJson(exchange, 200, latest);
                    return;
                }

                sendError(exchange, 404, "not_found", "요청한 API를 찾을 수 없습니다.");
            } catch (Exception ex) {
                handleFailure(exchange, ex);
            }
        }
    }

    private static final class AdminHandler extends BaseHandler {
        private final DatabaseRepository database;
        private final AdminService adminService;

        private AdminHandler(DatabaseRepository database, AdminService adminService) {
            this.database = database;
            this.adminService = adminService;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String path = exchange.getRequestURI().getPath();
                String method = exchange.getRequestMethod();

                if (path.equals("/api/admin/login")) {
                    if (!requireMethod(exchange, "POST")) {
                        return;
                    }
                    LoginResult result = adminService.login(clientKey(exchange), readJsonBody(exchange).get("id"));
                    if (result.status == LoginStatus.RATE_LIMITED) {
                        sendError(exchange, 429, "rate_limited", "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해주세요.");
                        return;
                    }
                    if (result.status == LoginStatus.UNAUTHORIZED) {
                        sendError(exchange, 401, "unauthorized", "관리자 ID가 올바르지 않습니다.");
                        return;
                    }
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("token", result.token);
                    body.put("adminEmail", ADMIN_EMAIL);
                    body.put("expiresAt", result.expiresAt.toString());
                    sendJson(exchange, 200, body);
                    return;
                }

                if (!requireAdmin(exchange, adminService)) {
                    return;
                }

                if (path.equals("/api/admin/recommendations")) {
                    if ("GET".equalsIgnoreCase(method)) {
                        sendJson(exchange, 200, database.listRecommendations(limit(exchange)));
                        return;
                    }
                    if ("POST".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
                        Map<String, Object> body = new LinkedHashMap<>();
                        body.put("deleted", database.deleteRecommendations(parseIds(readBody(exchange))));
                        sendJson(exchange, 200, body);
                        return;
                    }
                }

                if (path.equals("/api/admin/inquiries")) {
                    if ("GET".equalsIgnoreCase(method)) {
                        sendJson(exchange, 200, database.listInquiries(limit(exchange)));
                        return;
                    }
                    if ("POST".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
                        Map<String, Object> body = new LinkedHashMap<>();
                        body.put("deleted", database.deleteInquiries(parseIds(readBody(exchange))));
                        sendJson(exchange, 200, body);
                        return;
                    }
                }

                if (path.equals("/api/admin/decision")) {
                    if (!requireMethod(exchange, "POST")) {
                        return;
                    }
                    Map<String, String> body = readJsonBody(exchange);
                    long recommendationId = parsePositiveLong(body.get("recommendationId"), "recommendationId");
                    String publishAt = requiredText(body, "publishAt", 64);
                    Instant.parse(publishAt);
                    if (database.getRecommendationById(recommendationId) == null) {
                        sendError(exchange, 404, "not_found", "추천 항목을 찾을 수 없습니다.");
                        return;
                    }
                    database.upsertMenuDecisionSetting(recommendationId, publishAt);
                    sendJson(exchange, 200, Map.of("ok", true));
                    return;
                }

                sendError(exchange, 404, "not_found", "요청한 API를 찾을 수 없습니다.");
            } catch (Exception ex) {
                handleFailure(exchange, ex);
            }
        }

        private int limit(HttpExchange exchange) {
            String query = exchange.getRequestURI().getRawQuery();
            if (query == null) {
                return 100;
            }
            for (String part : query.split("&")) {
                String[] pair = part.split("=", 2);
                if (pair.length == 2 && "limit".equals(pair[0])) {
                    try {
                        return Math.max(1, Math.min(Integer.parseInt(pair[1]), 200));
                    } catch (NumberFormatException ignored) {
                        return 100;
                    }
                }
            }
            return 100;
        }

        private List<Long> parseIds(String body) {
            try {
                Map<String, String> parsed = JsonUtil.parseFlatObject(body);
                String idsText = parsed.get("ids");
                if (idsText == null || idsText.isBlank()) {
                    idsText = body;
                }
                String clean = idsText.replaceAll("[^0-9,]", "");
                if (clean.isBlank()) {
                    return Collections.emptyList();
                }
                List<Long> ids = new ArrayList<>();
                for (String value : clean.split(",")) {
                    if (!value.isBlank()) {
                        ids.add(Long.parseLong(value));
                    }
                }
                return ids;
            } catch (Exception ex) {
                return Collections.emptyList();
            }
        }
    }

    private static final class HealthHandler extends BaseHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            sendJson(exchange, 200, Map.of("status", "ok", "version", "2026-06-15"));
        }
    }

    private static final class AdminService {
        private final String adminIdHash;
        private final SecureRandom random = new SecureRandom();
        private final Map<String, Instant> activeTokens = new ConcurrentHashMap<>();
        private final Map<String, LoginFailureWindow> loginFailures = new ConcurrentHashMap<>();

        private AdminService(String adminIdHash) {
            this.adminIdHash = adminIdHash == null ? "" : adminIdHash.trim().toLowerCase(Locale.ROOT);
        }

        LoginResult login(String clientKey, String id) {
            Instant now = Instant.now();
            LoginFailureWindow window = loginFailures.compute(clientKey, (key, existing) -> {
                if (existing == null || now.isAfter(existing.startedAt.plusSeconds(LOGIN_FAIL_WINDOW_SECONDS))) {
                    return new LoginFailureWindow(now, 0);
                }
                return existing;
            });
            if (window.failures >= LOGIN_FAIL_LIMIT) {
                return LoginResult.rateLimited();
            }

            if (!constantTimeEquals(sha256Hex(id == null ? "" : id), adminIdHash)) {
                window.failures++;
                return LoginResult.unauthorized();
            }

            loginFailures.remove(clientKey);
            byte[] bytes = new byte[ADMIN_TOKEN_BYTES];
            random.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            Instant expiresAt = now.plusSeconds(ADMIN_SESSION_SECONDS);
            activeTokens.put(token, expiresAt);
            return LoginResult.success(token, expiresAt);
        }

        boolean isValidBearer(String authorization) {
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                return false;
            }
            String token = authorization.substring("Bearer ".length()).trim();
            Instant expiresAt = activeTokens.get(token);
            if (expiresAt == null) {
                return false;
            }
            if (Instant.now().isAfter(expiresAt)) {
                activeTokens.remove(token);
                return false;
            }
            return true;
        }

        private boolean constantTimeEquals(String a, String b) {
            byte[] left = a.getBytes(StandardCharsets.UTF_8);
            byte[] right = b.getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(left, right);
        }
    }

    private static final class PublicWriteGuard {
        private final Map<String, RequestWindow> windows = new ConcurrentHashMap<>();

        boolean allow(String endpoint, String clientKey, int limit) {
            Instant now = Instant.now();
            String key = endpoint + ":" + clientKey;
            RequestWindow window = windows.compute(key, (ignored, existing) -> {
                if (existing == null || now.isAfter(existing.startedAt.plusSeconds(PUBLIC_WRITE_WINDOW_SECONDS))) {
                    return new RequestWindow(now, 1);
                }
                existing.requests++;
                return existing;
            });
            return window.requests <= limit;
        }
    }

    private static final class RequestWindow {
        private final Instant startedAt;
        private int requests;

        private RequestWindow(Instant startedAt, int requests) {
            this.startedAt = startedAt;
            this.requests = requests;
        }
    }

    private enum LoginStatus {
        SUCCESS,
        UNAUTHORIZED,
        RATE_LIMITED
    }

    private static final class LoginResult {
        private final LoginStatus status;
        private final String token;
        private final Instant expiresAt;

        private LoginResult(LoginStatus status, String token, Instant expiresAt) {
            this.status = status;
            this.token = token;
            this.expiresAt = expiresAt;
        }

        static LoginResult success(String token, Instant expiresAt) {
            return new LoginResult(LoginStatus.SUCCESS, token, expiresAt);
        }

        static LoginResult unauthorized() {
            return new LoginResult(LoginStatus.UNAUTHORIZED, "", Instant.EPOCH);
        }

        static LoginResult rateLimited() {
            return new LoginResult(LoginStatus.RATE_LIMITED, "", Instant.EPOCH);
        }
    }

    private static final class LoginFailureWindow {
        private final Instant startedAt;
        private int failures;

        private LoginFailureWindow(Instant startedAt, int failures) {
            this.startedAt = startedAt;
            this.failures = failures;
        }
    }

    private static final class PayloadTooLargeException extends IOException {
    }

    private static String requiredText(Map<String, String> body, String key, int maxLength) {
        String value = cleanText(body.get(key), maxLength);
        if (value.isBlank()) {
            throw new IllegalArgumentException(key + " 값이 필요합니다.");
        }
        return value;
    }

    private static String cleanText(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException("입력값이 너무 깁니다.");
        }
        return trimmed;
    }

    private static long parsePositiveLong(String value, String fieldName) {
        try {
            long parsed = Long.parseLong(value == null ? "" : value);
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " 값이 올바르지 않습니다.");
        }
    }

    private static String clientKey(HttpExchange exchange) {
        if ("true".equalsIgnoreCase(System.getenv("TRUST_PROXY_HEADERS"))) {
            String forwardedFor = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
            if (forwardedFor != null) {
                String candidate = forwardedFor.split(",", 2)[0].trim();
                if (candidate.matches("[0-9a-fA-F:.]{1,64}")) {
                    return candidate;
                }
            }
        }
        InetSocketAddress address = exchange.getRemoteAddress();
        if (address == null || address.getAddress() == null) {
            return "unknown";
        }
        return address.getAddress().getHostAddress();
    }

    private static String recommendMenu(String menuName, String reason) {
        String source = (menuName + " " + reason).toLowerCase(Locale.ROOT);
        if (source.contains("매운") || source.contains("떡볶") || source.contains("spicy")) {
            return "떡볶이";
        }
        if (source.contains("채소") || source.contains("야채") || source.contains("비빔")) {
            return "비빔밥";
        }
        if (source.contains("국") || source.contains("따뜻")) {
            return "김치찌개";
        }
        if (source.contains("면") || source.contains("noodle")) {
            return "잔치국수";
        }
        return "비빔밥";
    }
}
