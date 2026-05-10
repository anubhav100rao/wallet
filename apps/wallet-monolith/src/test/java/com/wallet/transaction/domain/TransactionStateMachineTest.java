package com.wallet.transaction.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TransactionStateMachineTest {

  private static Transaction tx() {
    return new Transaction(
        TransactionType.P2P_TRANSFER,
        UUID.randomUUID().toString(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        new BigDecimal("100.0000"),
        "USD");
  }

  @Test
  void newTransactionStartsInPending() {
    assertThat(tx().getState()).isEqualTo(TransactionState.PENDING);
  }

  @Test
  void rejectsZeroOrNegativeAmount() {
    assertThatThrownBy(
            () ->
                new Transaction(
                    TransactionType.P2P_TRANSFER,
                    "k",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    BigDecimal.ZERO,
                    "USD"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new Transaction(
                    TransactionType.P2P_TRANSFER,
                    "k",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new BigDecimal("-1"),
                    "USD"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest(name = "{0} → {1} is allowed")
  @CsvSource({
    "PENDING, HELD",
    "PENDING, POSTED", // deposit skips HELD
    "PENDING, FAILED",
    "HELD, POSTED",
    "HELD, COMPENSATED",
    "POSTED, SETTLED",
  })
  void allowsValidTransitions(TransactionState from, TransactionState to) {
    Transaction t = tx();
    forceState(t, from);
    t.advanceState(to);
    assertThat(t.getState()).isEqualTo(to);
  }

  @ParameterizedTest(name = "{0} → {1} is rejected")
  @CsvSource({
    "PENDING, SETTLED",
    "PENDING, COMPENSATED",
    "HELD, SETTLED",
    "HELD, FAILED",
    "POSTED, COMPENSATED",
    "POSTED, FAILED",
    "POSTED, HELD",
    "SETTLED, POSTED",
    "SETTLED, FAILED",
    "FAILED, PENDING",
    "FAILED, HELD",
    "COMPENSATED, POSTED",
    "COMPENSATED, SETTLED",
  })
  void rejectsInvalidTransitions(TransactionState from, TransactionState to) {
    Transaction t = tx();
    forceState(t, from);
    assertThatThrownBy(() -> t.advanceState(to)).isInstanceOf(IllegalStateException.class);
    assertThat(t.getState()).isEqualTo(from);
  }

  @Test
  void terminalStatesAreSticky() {
    for (TransactionState terminal :
        new TransactionState[] {
          TransactionState.SETTLED, TransactionState.FAILED, TransactionState.COMPENSATED
        }) {
      Transaction t = tx();
      forceState(t, terminal);
      // re-entering the same terminal state is idempotent (no-op)
      t.advanceState(terminal);
      assertThat(t.getState()).isEqualTo(terminal);
      // but transitioning out is forbidden
      for (TransactionState other : TransactionState.values()) {
        if (other == terminal) continue;
        assertThatThrownBy(() -> t.advanceState(other))
            .as("%s -> %s must fail", terminal, other)
            .isInstanceOf(IllegalStateException.class);
      }
    }
  }

  @Test
  void canonicalTransferPath() {
    Transaction t = tx();
    t.advanceState(TransactionState.HELD);
    t.advanceState(TransactionState.POSTED);
    t.advanceState(TransactionState.SETTLED);
    assertThat(t.getState()).isEqualTo(TransactionState.SETTLED);
  }

  @Test
  void depositPathSkipsHeld() {
    Transaction t = tx();
    t.advanceState(TransactionState.POSTED);
    t.advanceState(TransactionState.SETTLED);
    assertThat(t.getState()).isEqualTo(TransactionState.SETTLED);
  }

  @Test
  void compensationPathFromHeld() {
    Transaction t = tx();
    t.advanceState(TransactionState.HELD);
    t.advanceState(TransactionState.COMPENSATED);
    assertThat(t.getState()).isEqualTo(TransactionState.COMPENSATED);
  }

  // The state field has a private setter via JPA; reflectively force it for tests so each scenario
  // can start at the desired state without manufacturing the path.
  private static void forceState(Transaction t, TransactionState s) {
    try {
      var f = Transaction.class.getDeclaredField("state");
      f.setAccessible(true);
      f.set(t, s);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
