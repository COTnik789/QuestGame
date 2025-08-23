package com.example.questgame.exception;

import com.example.questgame.dto.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Единая обработка ошибок без изменения контрактов.
 * Возвращает ApiError с кодом/сообщением и корректным HTTP-статусом.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(GameException.class)
    public Mono<ApiError> handleGame(GameException ex, ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        ErrorCode code = ex.getCode() == null ? ErrorCode.INTERNAL_ERROR : ex.getCode();
        HttpStatus status = mapStatus(code);
        ApiError body = ApiError.of(
                path,
                status.value(),
                status.getReasonPhrase(),
                code.name(),
                ex.getMessage() != null ? ex.getMessage() : status.getReasonPhrase(),
                Map.of()
        );
        if (status.is5xxServerError()) {
            log.error("GameException @ {} -> {} {}: {}", path, status.value(), code, body.getMessage(), ex);
        } else {
            log.warn("GameException @ {} -> {} {}: {}", path, status.value(), code, body.getMessage());
        }
        exchange.getResponse().setStatusCode(status);
        return Mono.just(body);
    }

    @ExceptionHandler(AuthenticationException.class)
    public Mono<ApiError> handleAuth(AuthenticationException ex, ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        ApiError body = ApiError.of(
                path,
                status.value(),
                status.getReasonPhrase(),
                ErrorCode.UNAUTHORIZED.name(),
                ex.getMessage() != null ? ex.getMessage() : "Unauthorized",
                Map.of()
        );
        log.warn("Auth error @ {} -> 401: {}", path, body.getMessage());
        exchange.getResponse().setStatusCode(status);
        return Mono.just(body);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ApiError> handleBind(WebExchangeBindException ex, ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ApiError body = ApiError.of(
                path,
                status.value(),
                status.getReasonPhrase(),
                ErrorCode.VALIDATION_FAILED.name(),
                "Validation failed",
                Map.of("errors", ex.getAllErrors())
        );
        log.warn("Validation error @ {} -> 400", path);
        exchange.getResponse().setStatusCode(status);
        return Mono.just(body);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ApiError> handleRse(ResponseStatusException ex, ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        HttpStatus status = ex.getStatusCode() instanceof HttpStatus hs ? hs : HttpStatus.INTERNAL_SERVER_ERROR;
        ErrorCode code = switch (status) {
            case UNAUTHORIZED -> ErrorCode.UNAUTHORIZED;
            case FORBIDDEN -> ErrorCode.FORBIDDEN;
            case NOT_FOUND -> ErrorCode.NOT_FOUND;
            case BAD_REQUEST -> ErrorCode.VALIDATION_FAILED;
            default -> ErrorCode.INTERNAL_ERROR;
        };
        ApiError body = ApiError.of(
                path,
                status.value(),
                status.getReasonPhrase(),
                code.name(),
                ex.getReason() != null ? ex.getReason() : status.getReasonPhrase(),
                Map.of()
        );
        if (status.is5xxServerError()) {
            log.error("RSE @ {} -> {} {}: {}", path, status.value(), code, body.getMessage(), ex);
        } else {
            log.warn("RSE @ {} -> {} {}: {}", path, status.value(), code, body.getMessage());
        }
        exchange.getResponse().setStatusCode(status);
        return Mono.just(body);
    }

    @ExceptionHandler(Throwable.class)
    public Mono<ApiError> handleGeneric(Throwable ex, ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        ApiError body = ApiError.of(
                path,
                status.value(),
                status.getReasonPhrase(),
                ErrorCode.INTERNAL_ERROR.name(),
                "Internal server error",
                Map.of()
        );
        log.error("Unhandled error @ {} -> 500: {}", path, ex.toString(), ex);
        exchange.getResponse().setStatusCode(status);
        return Mono.just(body);
    }

    private HttpStatus mapStatus(ErrorCode code) {
        return switch (code) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case VALIDATION_FAILED, BUSINESS_RULE_VIOLATION -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
