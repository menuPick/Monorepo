package menupick.web;

import menupick.web.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 모든 오류를 기존 서버와 같은 {"error","message"} JSON으로 변환합니다. */
@RestControllerAdvice
public class ApiErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(ex.status());
        if (ex.retryAfter() != null) {
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfter().toSeconds()));
        }
        return builder.body(new ErrorResponse(ex.code(), ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        if (BodySizeLimitFilter.isPayloadTooLarge(ex)) {
            return BodySizeLimitFilter.payloadTooLarge();
        }
        return error(HttpStatus.BAD_REQUEST, "bad_request", "요청 JSON 형식이 올바르지 않습니다.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "method_not_allowed", "지원하지 않는 요청 방식입니다.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_media_type", "Content-Type: application/json 이 필요합니다.");
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ResponseEntity<ErrorResponse> handleNotFound(Exception ex) {
        return error(HttpStatus.NOT_FOUND, "not_found", "요청한 API를 찾을 수 없습니다.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        if (BodySizeLimitFilter.isPayloadTooLarge(ex)) {
            return BodySizeLimitFilter.payloadTooLarge();
        }
        // 요청 본문/토큰은 남기지 않고 예외 종류와 위치만 기록합니다.
        log.error("Unhandled server error: {}", ex.toString(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "server_error", "서버 오류가 발생했습니다.");
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }
}
