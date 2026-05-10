package com.wallet.shared.money;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import net.jqwik.api.*;

/**
 * Property-based tests for Money using jqwik.
 *
 * <p>These tests verify algebraic properties that must hold for all valid Money values, not just
 * hand-picked examples. They guard against edge cases that unit tests miss.
 */
@Label("Money algebraic properties")
class MoneyPropertyTest {

  // ── Arbitrary providers ────────────────────────────────────────

  @Provide
  Arbitrary<BigDecimal> amounts() {
    return Arbitraries.bigDecimals()
        .between(new BigDecimal("-999999999999999.9999"), new BigDecimal("999999999999999.9999"))
        .ofScale(4);
  }

  @Provide
  Arbitrary<Currency> currencies() {
    return Arbitraries.of(Currency.values());
  }

  // ── Properties ─────────────────────────────────────────────────

  @Property
  @Label("commutativity: a + b == b + a")
  void additionIsCommutative(
      @ForAll("amounts") BigDecimal a,
      @ForAll("amounts") BigDecimal b,
      @ForAll("currencies") Currency c) {
    Money ma = Money.of(a, c);
    Money mb = Money.of(b, c);
    assertThat(ma.add(mb)).isEqualTo(mb.add(ma));
  }

  @Property
  @Label("identity: a + zero == a")
  void zeroIsAdditiveIdentity(@ForAll("amounts") BigDecimal a, @ForAll("currencies") Currency c) {
    Money ma = Money.of(a, c);
    Money zero = Money.zero(c);
    assertThat(ma.add(zero)).isEqualTo(ma);
  }

  @Property
  @Label("inverse: a + (-a) == zero")
  void negateIsAdditiveInverse(@ForAll("amounts") BigDecimal a, @ForAll("currencies") Currency c) {
    Money ma = Money.of(a, c);
    Money result = ma.add(ma.negate());
    assertThat(result.isZero()).isTrue();
  }

  @Property
  @Label("no Double in the chain: amount type is always BigDecimal")
  void noDoubleInChain(
      @ForAll("amounts") BigDecimal a,
      @ForAll("amounts") BigDecimal b,
      @ForAll("currencies") Currency c) {
    Money ma = Money.of(a, c);
    Money mb = Money.of(b, c);

    Money sum = ma.add(mb);
    Money diff = ma.subtract(mb);
    Money neg = ma.negate();
    Money product = ma.multiply(new BigDecimal("1.5"));

    // Verify the internal type is BigDecimal, not a lossy type
    assertThat(sum.getAmount()).isInstanceOf(BigDecimal.class);
    assertThat(diff.getAmount()).isInstanceOf(BigDecimal.class);
    assertThat(neg.getAmount()).isInstanceOf(BigDecimal.class);
    assertThat(product.getAmount()).isInstanceOf(BigDecimal.class);

    // Verify scale is preserved
    assertThat(sum.getAmount().scale()).isEqualTo(Money.SCALE);
    assertThat(diff.getAmount().scale()).isEqualTo(Money.SCALE);
    assertThat(neg.getAmount().scale()).isEqualTo(Money.SCALE);
    assertThat(product.getAmount().scale()).isEqualTo(Money.SCALE);
  }

  @Property
  @Label("associativity: (a + b) + c == a + (b + c)")
  void additionIsAssociative(
      @ForAll("amounts") BigDecimal a,
      @ForAll("amounts") BigDecimal b,
      @ForAll("amounts") BigDecimal c,
      @ForAll("currencies") Currency cur) {
    Money ma = Money.of(a, cur);
    Money mb = Money.of(b, cur);
    Money mc = Money.of(c, cur);
    assertThat(ma.add(mb).add(mc)).isEqualTo(ma.add(mb.add(mc)));
  }

  @Property
  @Label("double negation: -(-a) == a")
  void doubleNegation(@ForAll("amounts") BigDecimal a, @ForAll("currencies") Currency c) {
    Money ma = Money.of(a, c);
    assertThat(ma.negate().negate()).isEqualTo(ma);
  }
}
