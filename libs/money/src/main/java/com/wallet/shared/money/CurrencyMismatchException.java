package com.wallet.shared.money;

/**
 * Thrown when an operation is attempted on two Money values with different currencies.
 *
 * <p>This is a domain exception, not an {@link IllegalArgumentException}. Currency mismatches are
 * not programming errors — they are business rule violations that callers must handle explicitly.
 */
public class CurrencyMismatchException extends RuntimeException {

  private final Currency expected;
  private final Currency actual;

  public CurrencyMismatchException(Currency expected, Currency actual) {
    super(
        String.format(
            "Currency mismatch: expected %s but got %s", expected.getCode(), actual.getCode()));
    this.expected = expected;
    this.actual = actual;
  }

  public Currency getExpected() {
    return expected;
  }

  public Currency getActual() {
    return actual;
  }
}
