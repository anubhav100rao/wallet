package com.wallet.shared.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Typed wrapper around {@link MeterRegistry} for banking-domain meters. Every metric name lives
 * here so renames are a single edit and Grafana dashboards have one source of truth.
 */
@Component
public class BankingMetrics {

  // Timers
  private static final String WALLET_OP_TIMER = "banking.wallet.op";
  private static final String LEDGER_POST_TIMER = "banking.ledger.post";

  // Counters
  private static final String SAGA_TRANSITION = "banking.saga.transition";
  private static final String SAGA_FAILURE = "banking.saga.failure";

  private final MeterRegistry registry;

  public BankingMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  /** Times a wallet op (placeHold / releaseHold / capture / credit). */
  public <T> T timeWalletOp(String op, Supplier<T> work) {
    long start = System.nanoTime();
    String outcome = "ok";
    try {
      return work.get();
    } catch (RuntimeException e) {
      outcome = "error";
      throw e;
    } finally {
      Timer.builder(WALLET_OP_TIMER)
          .tags("op", op, "outcome", outcome)
          .register(registry)
          .record(Duration.ofNanos(System.nanoTime() - start));
    }
  }

  /** Times a void wallet op. */
  public void timeWalletOp(String op, Runnable work) {
    timeWalletOp(
        op,
        () -> {
          work.run();
          return null;
        });
  }

  /** Times a ledger post; outcome is one of {@code ok}, {@code error}, {@code duplicate}. */
  public void recordLedgerPost(String outcome, Duration elapsed) {
    Timer.builder(LEDGER_POST_TIMER).tags("outcome", outcome).register(registry).record(elapsed);
  }

  /** Counter for saga state transitions; saga is one of {@code transfer,deposit,withdrawal}. */
  public void recordTransition(String saga, String from, String to) {
    Counter.builder(SAGA_TRANSITION)
        .tags(Tags.of("saga", saga, "from", from, "to", to))
        .register(registry)
        .increment();
  }

  /** Counter for saga failures; {@code atState} captures where in the saga it failed. */
  public void recordFailure(String saga, String atState, String reason) {
    Counter.builder(SAGA_FAILURE)
        .tags(Tags.of("saga", saga, "at_state", atState, "reason", reason))
        .register(registry)
        .increment();
  }
}
