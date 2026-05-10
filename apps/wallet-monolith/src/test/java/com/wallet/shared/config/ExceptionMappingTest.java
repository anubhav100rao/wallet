package com.wallet.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.wallet.shared.idempotency.IdempotencyKeyMismatchException;
import com.wallet.shared.money.Currency;
import com.wallet.shared.money.CurrencyMismatchException;
import com.wallet.wallet.domain.InsufficientFundsException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

/**
 * Pins down the exception → HTTP-status mapping table. If a handler is added or moved, this test
 * fails — keep it in lock-step with {@code GlobalExceptionHandler}'s table-of-contents javadoc.
 */
class ExceptionMappingTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  private static void assertProblem(
      ResponseEntity<ProblemDetail> response, HttpStatus status, String type) {
    assertThat(response.getStatusCode()).isEqualTo(status);
    ProblemDetail body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getTitle()).isEqualTo(type);
    assertThat(body.getStatus()).isEqualTo(status.value());
    assertThat(body.getType().toString())
        .isEqualTo("https://banking-wallet.local/problems/" + type);
  }

  @Test
  void illegalArgument_isBadRequest() {
    assertProblem(
        handler.handleIllegalArgument(new IllegalArgumentException("bad")),
        HttpStatus.BAD_REQUEST,
        "bad_request");
  }

  @Test
  void illegalState_isConflict() {
    assertProblem(
        handler.handleIllegalState(new IllegalStateException("conflict")),
        HttpStatus.CONFLICT,
        "illegal_state");
  }

  @Test
  void idempotencyKeyMismatch_isConflict() {
    assertProblem(
        handler.handleIdempotency(new IdempotencyKeyMismatchException("dup")),
        HttpStatus.CONFLICT,
        "idempotency_conflict");
  }

  @Test
  void insufficientFunds_isUnprocessableEntity() {
    assertProblem(
        handler.handleInsufficientFunds(new InsufficientFundsException("nope")),
        HttpStatus.UNPROCESSABLE_ENTITY,
        "insufficient_funds");
  }

  @Test
  void currencyMismatch_isUnprocessableEntity() {
    assertProblem(
        handler.handleCurrencyMismatch(new CurrencyMismatchException(Currency.USD, Currency.EUR)),
        HttpStatus.UNPROCESSABLE_ENTITY,
        "currency_mismatch");
  }

  @Test
  void messageNotReadable_isBadRequest() {
    assertProblem(
        handler.handleUnreadable(new HttpMessageNotReadableException("malformed")),
        HttpStatus.BAD_REQUEST,
        "malformed_request");
  }

  @Test
  void constraintViolation_isBadRequest() {
    Validator validator;
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      validator = factory.getValidator();
    }
    var violations = validator.validate(new Sample(""));
    assertProblem(
        handler.handleConstraintViolation(new ConstraintViolationException(violations)),
        HttpStatus.BAD_REQUEST,
        "validation_failed");
  }

  @Test
  void noResourceFound_isNotFound() {
    var ex =
        new org.springframework.web.servlet.resource.NoResourceFoundException(
            org.springframework.http.HttpMethod.GET, "/missing");
    assertProblem(handler.handleNotFound(ex), HttpStatus.NOT_FOUND, "not_found");
  }

  @Test
  void methodNotAllowed_is405() {
    var ex = new org.springframework.web.HttpRequestMethodNotSupportedException("DELETE");
    assertProblem(
        handler.handleMethodNotAllowed(ex), HttpStatus.METHOD_NOT_ALLOWED, "method_not_allowed");
  }

  @Test
  void responseStatus_preservesItsStatus() {
    var ex =
        new org.springframework.web.server.ResponseStatusException(
            HttpStatus.PAYMENT_REQUIRED, "pay");
    var resp = handler.handleResponseStatus(ex);
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
  }

  @Test
  void uncaughtException_isInternalServerError() {
    assertProblem(
        handler.handleUnexpected(new RuntimeException("boom")),
        HttpStatus.INTERNAL_SERVER_ERROR,
        "internal_error");
  }

  static class Sample {
    @NotBlank String name;

    Sample(String name) {
      this.name = name;
    }
  }
}
