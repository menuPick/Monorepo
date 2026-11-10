package menupick.service;

import menupick.config.MenuPickProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 관리자 ID 로그인과 Bearer 토큰 세션(메모리 저장, 서버 재시작 시 초기화).
 * 관리자 ID는 SHA-256 해시로만 비교하며 원문을 저장하거나 로그에 남기지 않습니다.
 */
@Service
public class AdminAuthService {
    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);
    private static final int TOKEN_BYTES = 32;

    private final byte[] adminIdHash;
    private final Duration sessionTtl;
    private final int loginFailLimit;
    private final FixedWindowRateLimiter loginFailures;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Instant> activeTokens = new ConcurrentHashMap<>();

    public AdminAuthService(MenuPickProperties properties, Clock clock) {
        MenuPickProperties.Admin admin = properties.admin();
        this.adminIdHash = resolveAdminIdHash(admin).getBytes(StandardCharsets.UTF_8);
        this.sessionTtl = admin.sessionTtl();
        this.loginFailLimit = admin.loginFailLimit();
        this.loginFailures = new FixedWindowRateLimiter(admin.loginFailWindow(), clock);
        this.clock = clock;
    }

    public LoginResult login(String clientKey, String id) {
        if (loginFailures.count(clientKey) >= loginFailLimit) {
            return LoginResult.RATE_LIMITED;
        }
        byte[] candidate = Hashing.sha256Hex(id == null ? "" : id).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(candidate, adminIdHash)) {
            loginFailures.record(clientKey);
            return LoginResult.UNAUTHORIZED;
        }
        loginFailures.reset(clientKey);

        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = clock.instant().plus(sessionTtl);
        activeTokens.put(token, expiresAt);
        return LoginResult.success(token, expiresAt);
    }

    public boolean isValidBearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return false;
        }
        String token = authorization.substring("Bearer ".length()).trim();
        Instant expiresAt = activeTokens.get(token);
        if (expiresAt == null) {
            return false;
        }
        if (clock.instant().isAfter(expiresAt)) {
            activeTokens.remove(token, expiresAt);
            return false;
        }
        return true;
    }

    /** 만료된 토큰과 로그인 실패 기록을 주기적으로 정리합니다. */
    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT5M")
    public void evictExpired() {
        Instant now = clock.instant();
        activeTokens.values().removeIf(expiresAt -> now.isAfter(expiresAt));
        loginFailures.evictExpired();
    }

    private static String resolveAdminIdHash(MenuPickProperties.Admin admin) {
        if (StringUtils.hasText(admin.idHash())) {
            return admin.idHash().trim().toLowerCase(Locale.ROOT);
        }
        if (StringUtils.hasText(admin.idHashFile())) {
            try {
                String hash = Files.readString(Path.of(admin.idHashFile()), StandardCharsets.UTF_8).trim();
                if (!hash.isEmpty()) {
                    return hash.toLowerCase(Locale.ROOT);
                }
            } catch (IOException ex) {
                throw new UncheckedIOException("ADMIN_ID_HASH_FILE을 읽을 수 없습니다.", ex);
            }
        }
        if ("admin".equals(admin.id())) {
            log.warn("ADMIN_ID_HASH가 없어 기본 관리자 ID를 사용합니다. 로컬 개발에서만 사용하세요.");
        }
        return Hashing.sha256Hex(admin.id() == null ? "" : admin.id());
    }

    public record LoginResult(Status status, String token, Instant expiresAt) {
        public static final LoginResult UNAUTHORIZED = new LoginResult(Status.UNAUTHORIZED, "", Instant.EPOCH);
        public static final LoginResult RATE_LIMITED = new LoginResult(Status.RATE_LIMITED, "", Instant.EPOCH);

        static LoginResult success(String token, Instant expiresAt) {
            return new LoginResult(Status.SUCCESS, token, expiresAt);
        }

        public enum Status { SUCCESS, UNAUTHORIZED, RATE_LIMITED }
    }
}
