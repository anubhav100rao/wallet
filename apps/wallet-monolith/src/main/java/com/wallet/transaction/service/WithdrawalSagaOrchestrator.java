package com.wallet.transaction.service;

import com.wallet.ledger.api.LedgerApi;
import com.wallet.ledger.api.LedgerApi.LedgerEntry;
import com.wallet.ledger.api.SystemAccounts;
import com.wallet.shared.observability.BankingMetrics;
import com.wallet.transaction.domain.Transaction;
import com.wallet.transaction.domain.TransactionState;
import com.wallet.transaction.domain.TransactionType;
import com.wallet.transaction.event.LedgerPosted;
import com.wallet.transaction.event.TransferCompleted;
import com.wallet.transaction.event.TransferFailed;
import com.wallet.transaction.event.WalletCaptured;
import com.wallet.transaction.event.WalletHoldPlaced;
import com.wallet.transaction.event.WalletHoldReleased;
import com.wallet.transaction.event.WithdrawalRequested;
import com.wallet.transaction.repository.TransactionRepository;
import com.wallet.wallet.api.WalletApi;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Withdrawal saga: user wallet → external.
 *
 * <p>Flow: {@code WithdrawalRequested → wallet.placeHold(source) → HELD → ledger.post([source
 * -amount, SYSTEM_CASH +amount]) → POSTED → wallet.capture(source) → SETTLED}.
 *
 * <p>No destination wallet credit — external payout happens in a downstream system out of scope
 * here.
 */
@Service
public class WithdrawalSagaOrchestrator {

  private static final String SAGA = "withdrawal";

  private final WalletApi walletApi;
  private final LedgerApi ledgerApi;
  private final TransactionRepository transactionRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final BankingMetrics metrics;

  public WithdrawalSagaOrchestrator(
      WalletApi walletApi,
      LedgerApi ledgerApi,
      TransactionRepository transactionRepository,
      ApplicationEventPublisher eventPublisher,
      BankingMetrics metrics) {
    this.walletApi = walletApi;
    this.ledgerApi = ledgerApi;
    this.transactionRepository = transactionRepository;
    this.eventPublisher = eventPublisher;
    this.metrics = metrics;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onWithdrawalRequested(WithdrawalRequested event) {
    Transaction tx = transactionRepository.findById(event.transactionId()).orElseThrow();
    if (tx.getState() != TransactionState.PENDING) return;

    UUID holdId;
    try {
      holdId = walletApi.placeHold(tx.getFromWalletId(), tx.getId(), tx.getAmount());
    } catch (RuntimeException e) {
      advance(tx, TransactionState.FAILED);
      metrics.recordFailure(SAGA, "PENDING", e.getClass().getSimpleName());
      eventPublisher.publishEvent(
          new TransferFailed(tx.getId(), TransactionState.PENDING, e.getMessage()));
      return;
    }

    advance(tx, TransactionState.HELD);
    eventPublisher.publishEvent(new WalletHoldPlaced(tx.getId(), holdId));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onWalletHoldPlaced(WalletHoldPlaced event) {
    Transaction tx = transactionRepository.findById(event.transactionId()).orElseThrow();
    if (tx.getType() != TransactionType.WITHDRAWAL) return;
    if (tx.getState() != TransactionState.HELD) return;

    UUID systemCash = SystemAccounts.systemCash(tx.getCurrency());

    try {
      List<LedgerEntry> entries =
          List.of(
              new LedgerEntry(tx.getFromWalletId(), tx.getAmount().negate(), tx.getCurrency()),
              new LedgerEntry(systemCash, tx.getAmount(), tx.getCurrency()));
      ledgerApi.post(tx.getId(), entries, "{\"type\": \"WITHDRAWAL\"}");
    } catch (RuntimeException e) {
      walletApi.releaseHold(event.holdId());
      advance(tx, TransactionState.COMPENSATED);
      metrics.recordFailure(SAGA, "HELD", e.getClass().getSimpleName());
      eventPublisher.publishEvent(
          new WalletHoldReleased(tx.getId(), event.holdId(), "ledger_post_failed"));
      eventPublisher.publishEvent(
          new TransferFailed(tx.getId(), TransactionState.HELD, e.getMessage()));
      return;
    }

    advance(tx, TransactionState.POSTED);
    eventPublisher.publishEvent(new LedgerPosted(tx.getId()));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onLedgerPosted(LedgerPosted event) {
    Transaction tx = transactionRepository.findById(event.transactionId()).orElseThrow();
    if (tx.getType() != TransactionType.WITHDRAWAL) return;
    if (tx.getState() != TransactionState.POSTED) return;

    walletApi.captureByTransactionId(tx.getFromWalletId(), tx.getId());
    eventPublisher.publishEvent(new WalletCaptured(tx.getId()));

    advance(tx, TransactionState.SETTLED);
    eventPublisher.publishEvent(new TransferCompleted(tx.getId()));
  }

  private void advance(Transaction tx, TransactionState to) {
    TransactionState from = tx.getState();
    tx.advanceState(to);
    transactionRepository.save(tx);
    metrics.recordTransition(SAGA, from.name(), to.name());
  }
}
