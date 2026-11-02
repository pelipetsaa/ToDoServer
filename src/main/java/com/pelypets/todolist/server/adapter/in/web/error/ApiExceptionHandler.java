package com.pelypets.todolist.server.adapter.in.web.error;

import com.pelypets.todolist.server.adapter.in.web.ratelimit.RateLimitExceededException;
import com.pelypets.todolist.server.domain.exception.ConflictException;
import com.pelypets.todolist.server.domain.exception.DomainException;
import com.pelypets.todolist.server.domain.exception.EmailTakenException;
import com.pelypets.todolist.server.domain.exception.NotFoundException;
import com.pelypets.todolist.server.domain.exception.UnauthorizedException;
import com.pelypets.todolist.server.domain.exception.ValidationException;
import java.time.Instant;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Every error leaves the API as {@code {"code": ..., "message": ...}}, the contract of the Ktor server. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ApiError> handleDomain(DomainException e) {
        return switch (e) {
            case ValidationException v -> error(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, v.getMessage());
            case UnauthorizedException u -> error(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, u.getMessage());
            case NotFoundException n -> error(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, n.getMessage());
            case EmailTakenException t -> error(HttpStatus.CONFLICT, ErrorCode.EMAIL_TAKEN, t.getMessage());
            case ConflictException c -> error(HttpStatus.CONFLICT, ErrorCode.CONFLICT, c.getMessage());
        };
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ApiError> handleRateLimit(RateLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(e.retryAfterSeconds()))
                .body(body(ErrorCode.TOO_MANY_REQUESTS, "Too many requests, try again later"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleInvalidBody(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, message);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> handleInvalidArguments(
            HandlerMethodValidationException e) {
        String message = e.getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    ResponseEntity<ApiError> handleMalformed(Exception e) {
        return error(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Malformed request");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> handleMissingParameter(
            MissingServletRequestParameterException e) {
        return error(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                "Query parameter '" + e.getParameterName() + "' is required");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException e) {
        String expected = e.getRequiredType() == Instant.class ? "an ISO-8601 instant" : "valid";
        return error(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                "Query parameter '" + e.getName() + "' must be " + expected);
    }

    /** Framework errors such as 404 for an unknown path or 405 for a wrong method keep their status. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleOther(Exception e) {
        if (e instanceof ErrorResponse framework && framework.getStatusCode().is4xxClientError()) {
            HttpStatusCode status = framework.getStatusCode();
            ErrorCode code = switch (status.value()) {
                case 401 -> ErrorCode.UNAUTHORIZED;
                case 404 -> ErrorCode.NOT_FOUND;
                case 429 -> ErrorCode.TOO_MANY_REQUESTS;
                default -> ErrorCode.VALIDATION_ERROR;
            };
            HttpStatus resolved = HttpStatus.resolve(status.value());
            String reason = resolved != null ? resolved.getReasonPhrase() : "Bad request";
            return ResponseEntity.status(status).body(body(code, reason));
        }
        log.error("Unhandled error", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "Internal server error");
    }

    private static ResponseEntity<ApiError> error(
            HttpStatus status, ErrorCode code, String message) {
        return ResponseEntity.status(status).body(body(code, message));
    }

    private static ApiError body(ErrorCode code, String message) {
        return new ApiError(code, message);
    }
}
