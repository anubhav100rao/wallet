package com.wallet.wallet.service;

import com.wallet.shared.observability.BankingMetrics;
import com.wallet.wallet.domain.HoldState;
import com.wallet.wallet.domain.Wallet;
import com.wallet.wallet.domain.WalletCredit;
import com.wallet.wallet.domain.WalletHold;
import com.wallet.wallet.repository.WalletCreditRepository;
import com.wallet.wallet.repository.WalletHoldRepository;
import com.wallet.wallet.repository.WalletRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wallet operations exposed through {@link com.wallet.wallet.api.WalletApi}.
 *
 * <p>Two row-locking strategies live behind {@code wallet.locking.strategy}; see {@link
 * LockingStrategy}. The public methods (placeHold/releaseHold/capture/credit) wrap a {@code
 * REQUIRES_NEW} {@code …Once} method via the {@code self} proxy so each retry is its own
 * transaction (a transaction marked rollback-only by an optimistic-lock failure cannot be reused).
 */
@Service
public class WalletService {

  private static final int OPTIMISTIC_MAX_ATTEMPTS = 5;

  private final WalletRepository walletRepository;
  private final WalletHoldRepository walletHoldRepository;
  private final WalletCreditRepository walletCreditRepository;
  private final BankingMetrics metrics;
  private final WalletService self;
  private final LockingStrategy strategy;

  public WalletService(
      WalletRepository walletRepository,
      WalletHoldRepository walletHoldRepository,
      WalletCreditRepository walletCreditRepository,
      BankingMetrics metrics,
      @Lazy WalletService self,
      @Value("${wallet.locking.strategy:pessimistic}") String strategyName) {
    this.walletRepository = walletRepository;
    this.walletHoldRepository = walletHoldRepository;
    this.walletCreditRepository = walletCreditRepository;
    this.metrics = metrics;
    this.self = self;
    this.strategy = LockingStrategy.valueOf(strategyName.trim().toUpperCase());
  }

  // ── Public API ────────────────────────────────────────────────

  public UUID placeHold(UUID walletId, UUID transactionId, BigDecimal amount) {
    return metrics.timeWalletOp(
        "place_hold", () -> retry(() -> self.placeHoldOnce(walletId, transactionId, amount)));
  }

  public void releaseHold(UUID holdId) {
    metrics.timeWalletOp("release_hold", () -> retry(() -> self.releaseHoldOnce(holdId)));
  }

  public void capture(UUID holdId) {
    metrics.timeWalletOp("capture", () -> retry(() -> self.captureOnce(holdId)));
  }

  public void captureByTransactionId(UUID walletId, UUID transactionId) {
    metrics.timeWalletOp(
        "capture", () -> retry(() -> self.captureByTransactionIdOnce(walletId, transactionId)));
  }

  public void credit(UUID walletId, UUID transactionId, BigDecimal amount) {
    metrics.timeWalletOp(
        "credit", () -> retry(() -> self.creditOnce(walletId, transactionId, amount)));
  }

  // ── Per-attempt transactional implementations ─────────────────

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public UUID placeHoldOnce(UUID walletId, UUID transactionId, BigDecimal amount) {
    var existingHold = walletHoldRepository.findByWalletIdAndTransactionId(walletId, transactionId);
    if (existingHold.isPresent()) {
      return existingHold.get().getId();
    }

    Wallet wallet = acquireWallet(walletId);
    wallet.debitAvailable(amount);

    Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);
    WalletHold hold = new WalletHold(wallet, transactionId, amount, expiresAt);
    walletHoldRepository.save(hold);
    walletRepository.save(wallet);

    return hold.getId();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Void releaseHoldOnce(UUID holdId) {
    WalletHold hold =
        walletHoldRepository
            .findById(holdId)
            .orElseThrow(() -> new IllegalArgumentException("Hold not found: " + holdId));

    if (hold.getState() != HoldState.ACTIVE) {
      return null;
    }

    Wallet wallet = acquireWallet(hold.getWallet().getId());
    wallet.creditAvailable(hold.getAmount());
    hold.markReleased();

    walletRepository.save(wallet);
    walletHoldRepository.save(hold);
    return null;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Void captureOnce(UUID holdId) {
    WalletHold hold =
        walletHoldRepository
            .findById(holdId)
            .orElseThrow(() -> new IllegalArgumentException("Hold not found: " + holdId));
    doCapture(hold);
    return null;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Void captureByTransactionIdOnce(UUID walletId, UUID transactionId) {
    WalletHold hold =
        walletHoldRepository
            .findByWalletIdAndTransactionId(walletId, transactionId)
            .orElseThrow(
                () -> new IllegalArgumentException("Hold not found for tx: " + transactionId));
    doCapture(hold);
    return null;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Void creditOnce(UUID walletId, UUID transactionId, BigDecimal amount) {
    if (walletCreditRepository.existsByWalletIdAndTransactionId(walletId, transactionId)) {
      return null;
    }

    Wallet wallet = acquireWallet(walletId);
    wallet.creditTotalAndAvailable(amount);

    WalletCredit credit = new WalletCredit(wallet, transactionId, amount);
    walletCreditRepository.save(credit);
    walletRepository.save(wallet);
    return null;
  }

  // ── Internals ─────────────────────────────────────────────────

  private void doCapture(WalletHold hold) {
    if (hold.getState() == HoldState.CAPTURED) {
      return;
    }
    if (hold.getState() == HoldState.RELEASED) {
      throw new IllegalStateException("Cannot capture a released hold: " + hold.getId());
    }

    Wallet wallet = acquireWallet(hold.getWallet().getId());
    wallet.debitTotal(hold.getAmount());
    hold.markCaptured();

    walletRepository.save(wallet);
    walletHoldRepository.save(hold);
  }

  private Wallet acquireWallet(UUID walletId) {
    return (strategy == LockingStrategy.OPTIMISTIC
            ? walletRepository.findById(walletId)
            : walletRepository.findByIdForUpdate(walletId))
        .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + walletId));
  }

  /**
   * Retries optimistic-lock failures with linear backoff. Pessimistic strategy runs exactly once
   * (the row lock is the correctness guarantee).
   */
  private <T> T retry(Supplier<T> work) {
    int max = strategy == LockingStrategy.OPTIMISTIC ? OPTIMISTIC_MAX_ATTEMPTS : 1;
    for (int attempt = 0; attempt < max; attempt++) {
      try {
        return work.get();
      } catch (OptimisticLockingFailureException oles) {
        if (attempt == max - 1) {
          throw oles;
        }
        try {
          Thread.sleep(5L * (attempt + 1));
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new RuntimeException(ie);
        }
      }
    }
    throw new IllegalStateException("unreachable");
  }
}
