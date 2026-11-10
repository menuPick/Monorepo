package menupick.web;

import org.springframework.http.HttpStatus;

import java.time.Duration;

/** {"error": code, "message": message} 형태로 응답되는 예외. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Duration retryAfter;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(HttpStatus status, String code, String message, Duration retryAfter) {
        super(message, null, false, false);
        this.status = status;
        this.code = code;
        this.retryAfter = retryAfter;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "bad_request", message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "not_found", message);
    }

    public static ApiException rateLimited(String message, Duration retryAfter) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", message, retryAfter);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
