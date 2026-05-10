package com.wallet.transaction.service;

import com.wallet.ledger.api.LedgerApi;
import com.wallet.ledger.api.LedgerApi.LedgerEntry;
import com.wallet.shared.observability.BankingMetrics;
import com.wallet.transaction.domain.Transaction;
import com.wallet.transaction.domain.TransactionState;
import com.wallet.transaction.domain.TransactionType;
import com.wallet.transaction.event.LedgerPosted;
import com.wallet.transaction.event.TransferCompleted;
import com.wallet.transaction.event.TransferFailed;
import com.wallet.transaction.event.TransferRequested;
import com.wallet.transaction.event.WalletCaptured;
import com.wallet.transaction.event.WalletCredited;
import com.wallet.transaction.event.WalletHoldPlaced;
import com.wallet.transaction.event.WalletHoldReleased;
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
 * Internal-transfer saga: source wallet → destination wallet.
 *
 * <p>Flow: {@code TransferRequested → placeHold(source) → HELD → ledger.post → POSTED → capture +
 * credit → SETTLED}.
 *
 * <p>Compensation: any failure before {@code POSTED} releases the hold (if any) and lands in {@code
 * COMPENSATED}. After {@code POSTED} the books are correct; capture/credit are idempotent and
 * retried, never silently rolled back. Explicit reversal (out of scope here) would be a separate
 * saga that posts a reversing ledger entry.
 */
@Service
public class TransferSagaOrchestrator {

  private static final String SAGA = "transfer";

  private final WalletApi walletApi;
  private final LedgerApi ledgerApi;
  private final TransactionRepository transactionRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final BankingMetrics metrics;

  public TransferSagaOrchestrator(
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
  public void onTransferRequested(TransferRequested event) {
    Transaction tx = getTransaction(event.transactionId());
    if (tx.getState() != TransactionState.PENDING) return;

    UUID holdId;
    try {
      holdId = walletApi.placeHold(event.fromWalletId(), event.transactionId(), event.amount());
    } catch (RuntimeException e) {
      advance(tx, TransactionState.FAILED);
      metrics.recordFailure(SAGA, "PENDING", reasonOf(e));
      eventPublisher.publishEvent(
          new TransferFailed(tx.getId(), TransactionState.PENDING, e.getMessage()));
      return;
    }

    advance(tx, TransactionState.HELD);
    eventPublisher.publishEvent(new WalletHoldPlaced(event.transactionId(), holdId));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onWalletHoldPlaced(WalletHoldPlaced event) {
    Transaction tx = getTransaction(event.transactionId());
    if (tx.getType() != TransactionType.P2P_TRANSFER) return;
    if (tx.getState() != TransactionState.HELD) return;

    try {
      List<LedgerEntry> entries =
          List.of(
              new LedgerEntry(tx.getFromWalletId(), tx.getAmount().negate(), tx.getCurrency()),
              new LedgerEntry(tx.getToWalletId(), tx.getAmount(), tx.getCurrency()));
      ledgerApi.post(tx.getId(), entries, "{\"type\": \"P2P_TRANSFER\"}");
    } catch (RuntimeException e) {
      walletApi.releaseHold(event.holdId());
      advance(tx, TransactionState.COMPENSATED);
      metrics.recordFailure(SAGA, "HELD", reasonOf(e));
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
    Transaction tx = getTransaction(event.transactionId());
    if (tx.getType() != TransactionType.P2P_TRANSFER) return;
    if (tx.getState() != TransactionState.POSTED) return;

    walletApi.captureByTransactionId(tx.getFromWalletId(), tx.getId());
    eventPublisher.publishEvent(new WalletCaptured(tx.getId()));

    walletApi.credit(tx.getToWalletId(), tx.getId(), tx.getAmount());
    eventPublisher.publishEvent(new WalletCredited(tx.getId()));

    advance(tx, TransactionState.SETTLED);
    eventPublisher.publishEvent(new TransferCompleted(tx.getId()));
  }

  private Transaction getTransaction(UUID transactionId) {
    return transactionRepository.findById(transactionId).orElseThrow();
  }

  private void advance(Transaction tx, TransactionState to) {
    TransactionState from = tx.getState();
    tx.advanceState(to);
    transactionRepository.save(tx);
    metrics.recordTransition(SAGA, from.name(), to.name());
  }

  private static String reasonOf(RuntimeException e) {
    return e.getClass().getSimpleName();
  }
}
