package com.kobi.territory.api;

import com.kobi.territory.common.error.ErrorKind;
import com.kobi.territory.common.error.TerritoryException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 전역 예외 처리. 모든 오류를 {code, message} 로 응답한다.
 * ErrorKind → HTTP: INVALID 400, UNAUTHENTICATED 401, FORBIDDEN 403, NOT_FOUND 404, CONFLICT 409, RULE_VIOLATION 422.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TerritoryException.class)
    public ResponseEntity<ErrorResponse> handle(TerritoryException exception) {
        return respond(status(exception.kind()), exception.code(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handle(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
            .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "요청 값이 올바르지 않습니다 — " + detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handle(HttpMessageNotReadableException exception) {
        return respond(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "요청 본문을 읽을 수 없습니다(JSON·날짜 형식 확인).");
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingRequestHeaderException.class,
        MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleBadParam(Exception exception) {
        return respond(HttpStatus.BAD_REQUEST, "BAD_PARAMETER", exception.getMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handle(HttpRequestMethodNotSupportedException exception) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", exception.getMessage());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handle(HttpMediaTypeNotSupportedException exception) {
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", exception.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handle(NoResourceFoundException exception) {
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "없는 경로입니다: /" + exception.getResourcePath());
    }

    /**
     * 동시 요청이 UNIQUE에 걸린 경우의 범용 응답. 도메인 의미가 있는 제약(visit (지도, 지역, 멤버) UNIQUE → DUPLICATE_VISIT)은
     * 그 테이블을 아는 컨텍스트 infra 가 도메인 오류로 번역해 던진다(QA N3) — 여기는 제약 이름을 모른다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handle(DataIntegrityViolationException exception) {
        log.warn("무결성 위반: {}", exception.getMostSpecificCause().getMessage());
        return respond(HttpStatus.CONFLICT, "CONFLICT", "동시에 같은 요청이 처리되었습니다. 새로고침 후 다시 시도해 주세요.");
    }

    /** 동시성 충돌 — 낙관적 락 충돌·비관적 잠금 실패(진행·퀘스트 보드 동시 갱신 — 예: 보상 받기 두 번 동시 클릭). */
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ErrorResponse> handle(ConcurrencyFailureException exception) {
        log.debug("동시성 충돌(409): {}", exception.getMostSpecificCause().toString());
        return respond(HttpStatus.CONFLICT, "CONCURRENT_UPDATE", "동시에 같은 요청이 처리되었습니다. 새로고침 후 다시 시도해 주세요.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handle(Exception exception) {
        log.error("처리하지 못한 예외", exception);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 오류가 발생했습니다.");
    }

    static HttpStatus status(ErrorKind kind) {
        return switch (kind) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case RULE_VIOLATION -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
    }

    private static ResponseEntity<ErrorResponse> respond(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }
}
