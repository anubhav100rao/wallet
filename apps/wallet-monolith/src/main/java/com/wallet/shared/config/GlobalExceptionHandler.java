package com.wallet.shared.config;

import com.wallet.shared.idempotency.IdempotencyKeyMismatchException;
import com.wallet.shared.money.CurrencyMismatchException;
import com.wallet.wallet.domain.InsufficientFundsException;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps domain and framework exceptions to RFC 7807 {@code application/problem+json} responses.
 *
 * <p>Mapping table (kept in sync with {@code ExceptionMappingTest}):
 *
 * <pre>
 *   MethodArgumentNotValidException        → 400  validation_failed
 *   HandlerMethodValidationException       → 400  validation_failed
 *   ConstraintViolationException           → 400  validation_failed
 *   HttpMessageNotReadableException        → 400  malformed_request
 *   MissingRequestHeaderException          → 400  missing_request_value
 *   MissingServletRequestParameterException→ 400  missing_request_value
 *   ServletRequestBindingException         → 400  missing_request_value
 *   IllegalArgumentException               → 400  bad_request
 *   NoResourceFoundException               → 404  not_found
 *   HttpRequestMethodNotSupportedException → 405  method_not_allowed
 *   IdempotencyKeyMismatchException        → 409  idempotency_conflict
 *   IllegalStateException                  → 409  illegal_state  (also: invalid FSM transitions)
 *   InsufficientFundsException             → 422  insufficient_funds
 *   CurrencyMismatchException              → 422  currency_mismatch
 *   ResponseStatusException                → its own status, preserved
 *   Exception (catch-all)                  → 500  internal_error
 * </pre>
 *
 * <p>Domain exceptions live in 4xx; only truly unexpected failures fall through to 500. The 5xx
 * fallback also logs at ERROR with the full stack so on-call has something to grep for.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private static final URI PROBLEM_BASE = URI.create("https://banking-wallet.local/problems/");

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleBeanValidation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining(", "));
    return problem(HttpStatus.BAD_REQUEST, "validation_failed", message);
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<ProblemDetail> handleHandlerValidation(
      HandlerMethodValidationException ex) {
    // Spring 6.1+ raises this for @Valid on records and @Validated on parameters. Drill into the
    // per-result errors so the client sees which field(s) failed and why; FieldError carries the
    // record-component name, ResolvableError fallback covers param-level constraints.
    String message =
        ex.getAllValidationResults().stream()
            .flatMap(
                result ->
                    result.getResolvableErrors().stream()
                        .map(
                            err -> {
                              if (err instanceof org.springframework.validation.FieldError fe) {
                                return fe.getField() + ": " + fe.getDefaultMessage();
                              }
                              String param = result.getMethodParameter().getParameterName();
                              return (param == null ? "value" : param)
                                  + ": "
                                  + err.getDefaultMessage();
                            }))
            .collect(Collectors.joining(", "));
    if (message.isBlank()) {
      message = ex.getMessage();
    }
    return problem(HttpStatus.BAD_REQUEST, "validation_failed", message);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
    String message =
        ex.getConstraintViolations().stream()
            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
            .collect(Collectors.joining(", "));
    return problem(HttpStatus.BAD_REQUEST, "validation_failed", message);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex) {
    return problem(HttpStatus.BAD_REQUEST, "malformed_request", "Request body could not be parsed");
  }

  @ExceptionHandler({
    MissingRequestHeaderException.class,
    MissingServletRequestParameterException.class,
    ServletRequestBindingException.class
  })
  public ResponseEntity<ProblemDetail> handleMissingRequestValue(Exception ex) {
    return problem(HttpStatus.BAD_REQUEST, "missing_request_value", ex.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
    return problem(HttpStatus.BAD_REQUEST, "bad_request", ex.getMessage());
  }

  @ExceptionHandler(IdempotencyKeyMismatchException.class)
  public ResponseEntity<ProblemDetail> handleIdempotency(IdempotencyKeyMismatchException ex) {
    return problem(HttpStatus.CONFLICT, "idempotency_conflict", ex.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ProblemDetail> handleIllegalState(IllegalStateException ex) {
    return problem(HttpStatus.CONFLICT, "illegal_state", ex.getMessage());
  }

  @ExceptionHandler(InsufficientFundsException.class)
  public ResponseEntity<ProblemDetail> handleInsufficientFunds(InsufficientFundsException ex) {
    return problem(HttpStatus.UNPROCESSABLE_ENTITY, "insufficient_funds", ex.getMessage());
  }

  @ExceptionHandler(CurrencyMismatchException.class)
  public ResponseEntity<ProblemDetail> handleCurrencyMismatch(CurrencyMismatchException ex) {
    return problem(HttpStatus.UNPROCESSABLE_ENTITY, "currency_mismatch", ex.getMessage());
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ProblemDetail> handleNotFound(NoResourceFoundException ex) {
    return problem(
        HttpStatus.NOT_FOUND, "not_found", "Resource not found: " + ex.getResourcePath());
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ProblemDetail> handleMethodNotAllowed(
      HttpRequestMethodNotSupportedException ex) {
    return problem(HttpStatus.METHOD_NOT_ALLOWED, "method_not_allowed", ex.getMessage());
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ProblemDetail> handleResponseStatus(ResponseStatusException ex) {
    HttpStatus status =
        HttpStatus.resolve(ex.getStatusCode().value()) == null
            ? HttpStatus.INTERNAL_SERVER_ERROR
            : HttpStatus.valueOf(ex.getStatusCode().value());
    return problem(status, status.name().toLowerCase().replace(' ', '_'), ex.getReason());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
    log.error("Unhandled exception", ex);
    return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Internal server error");
  }

  private ResponseEntity<ProblemDetail> problem(HttpStatus status, String type, String detail) {
    String body = (detail == null || detail.isBlank()) ? type : detail;
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, body);
    pd.setTitle(type);
    pd.setType(PROBLEM_BASE.resolve(type));
    return ResponseEntity.status(status).body(pd);
  }
}
