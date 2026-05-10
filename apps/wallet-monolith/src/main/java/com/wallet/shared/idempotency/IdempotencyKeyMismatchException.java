package com.wallet.shared.idempotency;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class IdempotencyKeyMismatchException extends RuntimeException {

  public IdempotencyKeyMismatchException(String message) {
    super(message);
  }
}
