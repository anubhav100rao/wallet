package com.wallet.shared.money;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Money value object")
class MoneyTest {

  // ── Static factories ───────────────────────────────────────────

  @Nested
  @DisplayName("static factories")
  class Factories {

    @Test
    @DisplayName("of(BigDecimal, Currency) creates Money with scale 4")
    void ofBigDecimal() {
      Money m = Money.of(new BigDecimal("100.50"), Currency.INR);
      assertThat(m.getAmount()).isEqualByComparingTo("100.5000");
      assertThat(m.getAmount().scale()).isEqualTo(4);
      assertThat(m.getCurrency()).isEqualTo(Currency.INR);
    }

    @Test
    @DisplayName("of(String, Currency) parses string amount")
    void ofString() {
      Money m = Money.of("99.99", Currency.USD);
      assertThat(m.getAmount()).isEqualByComparingTo("99.9900");
      assertThat(m.getCurrency()).isEqualTo(Currency.USD);
    }

    @Test
    @DisplayName("zero(Currency) creates a zero-valued Money")
    void zero() {
      Money m = Money.zero(Currency.EUR);
      assertThat(m.isZero()).isTrue();
      assertThat(m.getAmount()).isEqualByComparingTo("0.0000");
      assertThat(m.getCurrency()).isEqualTo(Currency.EUR);
    }

    @Test
    @DisplayName("minor(long, Currency) converts from minor units")
    void minor() {
      // 10050 paise = 100.50 INR
      Money m = Money.minor(10050, Currency.INR);
      assertThat(m.getAmount()).isEqualByComparingTo("100.5000");
      assertThat(m.getCurrency()).isEqualTo(Currency.INR);
    }

    @Test
    @DisplayName("minor units for JPY (0 decimal places)")
    void minorJpy() {
      // 1000 yen = 1000 JPY (no minor units)
      Money m = Money.minor(1000, Currency.JPY);
      assertThat(m.getAmount()).isEqualByComparingTo("1000.0000");
    }

    @Test
    @DisplayName("null amount throws NullPointerException")
    void nullAmount() {
      assertThatNullPointerException()
          .isThrownBy(() -> Money.of((BigDecimal) null, Currency.INR))
          .withMessageContaining("amount");
    }

    @Test
    @DisplayName("null currency throws NullPointerException")
    void nullCurrency() {
      assertThatNullPointerException()
          .isThrownBy(() -> Money.of("100", null))
          .withMessageContaining("currency");
    }
  }

  // ── Arithmetic ─────────────────────────────────────────────────

  @Nested
  @DisplayName("arithmetic")
  class Arithmetic {

    @Test
    @DisplayName("add: 100 + 50 = 150")
    void add() {
      Money a = Money.of("100", Currency.INR);
      Money b = Money.of("50", Currency.INR);
      Money result = a.add(b);
      assertThat(result.getAmount()).isEqualByComparingTo("150.0000");
    }

    @Test
    @DisplayName("subtract: 100 - 30 = 70")
    void subtract() {
      Money a = Money.of("100", Currency.INR);
      Money b = Money.of("30", Currency.INR);
      Money result = a.subtract(b);
      assertThat(result.getAmount()).isEqualByComparingTo("70.0000");
    }

    @Test
    @DisplayName("negate: -(100) = -100")
    void negate() {
      Money m = Money.of("100", Currency.INR);
      Money neg = m.negate();
      assertThat(neg.getAmount()).isEqualByComparingTo("-100.0000");
      assertThat(neg.isNegative()).isTrue();
    }

    @Test
    @DisplayName("multiply: 100 × 1.5 = 150")
    void multiply() {
      Money m = Money.of("100", Currency.INR);
      Money result = m.multiply(new BigDecimal("1.5"));
      assertThat(result.getAmount()).isEqualByComparingTo("150.0000");
    }

    @Test
    @DisplayName("add with different currencies throws CurrencyMismatchException")
    void addCurrencyMismatch() {
      Money inr = Money.of("100", Currency.INR);
      Money usd = Money.of("100", Currency.USD);
      assertThatThrownBy(() -> inr.add(usd))
          .isInstanceOf(CurrencyMismatchException.class)
          .hasMessageContaining("INR")
          .hasMessageContaining("USD");
    }

    @Test
    @DisplayName("subtract with different currencies throws CurrencyMismatchException")
    void subtractCurrencyMismatch() {
      Money inr = Money.of("100", Currency.INR);
      Money eur = Money.of("50", Currency.EUR);
      assertThatThrownBy(() -> inr.subtract(eur)).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    @DisplayName("adding zero is identity")
    void addZeroIsIdentity() {
      Money m = Money.of("42.1234", Currency.USD);
      Money result = m.add(Money.zero(Currency.USD));
      assertThat(result).isEqualTo(m);
    }

    @Test
    @DisplayName("a + (-a) = zero")
    void addNegateIsZero() {
      Money m = Money.of("123.4567", Currency.EUR);
      Money result = m.add(m.negate());
      assertThat(result.isZero()).isTrue();
    }
  }

  // ── Queries ────────────────────────────────────────────────────

  @Nested
  @DisplayName("queries")
  class Queries {

    @Test
    void isPositive() {
      assertThat(Money.of("1", Currency.INR).isPositive()).isTrue();
      assertThat(Money.of("-1", Currency.INR).isPositive()).isFalse();
      assertThat(Money.zero(Currency.INR).isPositive()).isFalse();
    }

    @Test
    void isNegative() {
      assertThat(Money.of("-1", Currency.INR).isNegative()).isTrue();
      assertThat(Money.of("1", Currency.INR).isNegative()).isFalse();
      assertThat(Money.zero(Currency.INR).isNegative()).isFalse();
    }

    @Test
    void isGreaterThan() {
      Money a = Money.of("100", Currency.INR);
      Money b = Money.of("50", Currency.INR);
      assertThat(a.isGreaterThan(b)).isTrue();
      assertThat(b.isGreaterThan(a)).isFalse();
      assertThat(a.isGreaterThan(a)).isFalse();
    }

    @Test
    void isGreaterThanOrEqual() {
      Money a = Money.of("100", Currency.INR);
      Money b = Money.of("100", Currency.INR);
      assertThat(a.isGreaterThanOrEqual(b)).isTrue();
    }
  }

  // ── Equality ───────────────────────────────────────────────────

  @Nested
  @DisplayName("equality")
  class Equality {

    @Test
    @DisplayName("equals uses compareTo, not BigDecimal.equals")
    void equalsUsesCompareTo() {
      Money a = Money.of(new BigDecimal("100.00"), Currency.INR);
      Money b = Money.of(new BigDecimal("100.0000"), Currency.INR);
      assertThat(a).isEqualTo(b);
      assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("different currencies are not equal even with same amount")
    void differentCurrencyNotEqual() {
      Money inr = Money.of("100", Currency.INR);
      Money usd = Money.of("100", Currency.USD);
      assertThat(inr).isNotEqualTo(usd);
    }

    @Test
    @DisplayName("same amount and currency are equal")
    void sameAmountAndCurrency() {
      Money a = Money.of("42.1234", Currency.GBP);
      Money b = Money.of("42.1234", Currency.GBP);
      assertThat(a).isEqualTo(b);
    }
  }

  // ── toString ───────────────────────────────────────────────────

  @Test
  @DisplayName("toString produces 'amount CURRENCY' format")
  void toStringFormat() {
    Money m = Money.of("100.50", Currency.INR);
    assertThat(m.toString()).isEqualTo("100.5000 INR");
  }

  // ── Immutability ───────────────────────────────────────────────

  @Test
  @DisplayName("operations return new instances, original is unchanged")
  void immutability() {
    Money original = Money.of("100", Currency.INR);
    Money added = original.add(Money.of("50", Currency.INR));
    assertThat(original.getAmount()).isEqualByComparingTo("100.0000");
    assertThat(added.getAmount()).isEqualByComparingTo("150.0000");
  }
}
