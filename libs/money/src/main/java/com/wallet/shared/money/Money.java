package com.wallet.shared.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Immutable value object representing a monetary amount with its currency.
 *
 * <p>Design invariants:
 *
 * <ul>
 *   <li>Amount is always stored with scale 4 ({@link #SCALE}) and {@link RoundingMode#HALF_EVEN}.
 *   <li>Equality uses {@link BigDecimal#compareTo}, not {@link BigDecimal#equals}, so that {@code
 *       Money.of("100.00", INR).equals(Money.of("100.0000", INR))} is {@code true}.
 *   <li>All operations that combine two Money values require matching currencies — a {@link
 *       CurrencyMismatchException} is thrown on mismatch.
 *   <li>No {@code double} or {@code float} is ever used in any code path.
 * </ul>
 */
public final class Money {

  /** Standard scale for all money amounts: 4 decimal places. */
  public static final int SCALE = 4;

  /** Rounding mode: banker's rounding (half-even) to avoid systematic bias. */
  public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

  private final BigDecimal amount;
  private final Currency currency;

  private Money(BigDecimal amount, Currency currency) {
    Objects.requireNonNull(amount, "amount must not be null");
    Objects.requireNonNull(currency, "currency must not be null");
    this.amount = amount.setScale(SCALE, ROUNDING);
    this.currency = currency;
  }

  // ── Static factories ───────────────────────────────────────────

  /**
   * Creates a Money from a BigDecimal amount and a Currency.
   *
   * @throws NullPointerException if either argument is null
   */
  public static Money of(BigDecimal amount, Currency currency) {
    return new Money(amount, currency);
  }

  /**
   * Creates a Money from a string representation of the amount.
   *
   * @throws NumberFormatException if the string is not a valid number
   */
  public static Money of(String amount, Currency currency) {
    return new Money(new BigDecimal(amount), currency);
  }

  /** Creates a zero-valued Money in the given currency. */
  public static Money zero(Currency currency) {
    return new Money(BigDecimal.ZERO, currency);
  }

  /**
   * Creates a Money from minor units (e.g., paise for INR, cents for USD).
   *
   * <p>Example: {@code Money.minor(10050, Currency.INR)} produces 100.5000 INR.
   */
  public static Money minor(long minorUnits, Currency currency) {
    BigDecimal divisor = BigDecimal.TEN.pow(currency.getMinorUnits());
    BigDecimal amount = BigDecimal.valueOf(minorUnits).divide(divisor, SCALE, ROUNDING);
    return new Money(amount, currency);
  }

  // ── Arithmetic operations ──────────────────────────────────────

  /**
   * Adds another Money value to this one.
   *
   * @throws CurrencyMismatchException if currencies differ
   */
  public Money add(Money other) {
    requireSameCurrency(other);
    return new Money(this.amount.add(other.amount), this.currency);
  }

  /**
   * Subtracts another Money value from this one.
   *
   * @throws CurrencyMismatchException if currencies differ
   */
  public Money subtract(Money other) {
    requireSameCurrency(other);
    return new Money(this.amount.subtract(other.amount), this.currency);
  }

  /** Returns the negation of this Money value. */
  public Money negate() {
    return new Money(this.amount.negate(), this.currency);
  }

  /**
   * Multiplies this Money value by a scalar.
   *
   * <p>The scalar must be a BigDecimal — no double/float allowed.
   */
  public Money multiply(BigDecimal multiplier) {
    Objects.requireNonNull(multiplier, "multiplier must not be null");
    return new Money(this.amount.multiply(multiplier), this.currency);
  }

  // ── Queries ────────────────────────────────────────────────────

  /** Returns true if this amount is zero. */
  public boolean isZero() {
    return amount.compareTo(BigDecimal.ZERO) == 0;
  }

  /** Returns true if this amount is positive (greater than zero). */
  public boolean isPositive() {
    return amount.compareTo(BigDecimal.ZERO) > 0;
  }

  /** Returns true if this amount is negative (less than zero). */
  public boolean isNegative() {
    return amount.compareTo(BigDecimal.ZERO) < 0;
  }

  /**
   * Returns true if this Money is greater than the other.
   *
   * @throws CurrencyMismatchException if currencies differ
   */
  public boolean isGreaterThan(Money other) {
    requireSameCurrency(other);
    return this.amount.compareTo(other.amount) > 0;
  }

  /**
   * Returns true if this Money is greater than or equal to the other.
   *
   * @throws CurrencyMismatchException if currencies differ
   */
  public boolean isGreaterThanOrEqual(Money other) {
    requireSameCurrency(other);
    return this.amount.compareTo(other.amount) >= 0;
  }

  // ── Accessors ──────────────────────────────────────────────────

  public BigDecimal getAmount() {
    return amount;
  }

  public Currency getCurrency() {
    return currency;
  }

  // ── Internals ──────────────────────────────────────────────────

  private void requireSameCurrency(Money other) {
    Objects.requireNonNull(other, "other must not be null");
    if (this.currency != other.currency) {
      throw new CurrencyMismatchException(this.currency, other.currency);
    }
  }

  // ── equals / hashCode / toString ───────────────────────────────

  /**
   * Two Money values are equal if their currencies are the same and their amounts are numerically
   * equal (via {@link BigDecimal#compareTo}, not {@link BigDecimal#equals}).
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Money other)) return false;
    return this.currency == other.currency && this.amount.compareTo(other.amount) == 0;
  }

  @Override
  public int hashCode() {
    // Strip trailing zeros so that 100.00 and 100.0000 hash the same
    return Objects.hash(amount.stripTrailingZeros().toPlainString(), currency);
  }

  @Override
  public String toString() {
    return amount.toPlainString() + " " + currency.getCode();
  }
}
