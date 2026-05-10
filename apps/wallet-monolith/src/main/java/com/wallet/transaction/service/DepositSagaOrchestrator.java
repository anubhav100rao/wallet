package com.wallet.transaction.service;

import com.wallet.ledger.api.LedgerApi;
import com.wallet.ledger.api.LedgerApi.LedgerEntry;
import com.wallet.ledger.api.SystemAccounts;
import com.wallet.shared.observability.BankingMetrics;
import com.wallet.transaction.domain.Transaction;
import com.wallet.transaction.domain.TransactionState;
import com.wallet.transaction.domain.TransactionType;
import com.wallet.transaction.event.DepositRequested;
import com.wallet.transaction.event.LedgerPosted;
import com.wallet.transaction.event.TransferCompleted;
import com.wallet.transaction.event.TransferFailed;
import com.wallet.transaction.event.WalletCredited;
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
 * Deposit saga: external → user wallet.
 *
 * <p>Flow: {@code DepositRequested → ledger.post([SYSTEM_CASH -amount, dest +amount]) → POSTED →
 * wallet.credit(dest) → SETTLED}.
 *
 * <p>No {@code HELD} state — there is nothing to hold against (cash is arriving from outside the
 * system).
 */
@Service
public class DepositSagaOrchestrator {

  private static final String SAGA = "deposit";

  private final WalletApi walletApi;
  private final LedgerApi ledgerApi;
  private final TransactionRepository transactionRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final BankingMetrics metrics;

  public DepositSagaOrchestrator(
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
  public void onDepositRequested(DepositRequested event) {
    Transaction tx = transactionRepository.findById(event.transactionId()).orElseThrow();
    if (tx.getState() != TransactionState.PENDING) return;

    UUID systemCash = SystemAccounts.systemCash(tx.getCurrency());

    try {
      List<LedgerEntry> entries =
          List.of(
              new LedgerEntry(systemCash, tx.getAmount().negate(), tx.getCurrency()),
              new LedgerEntry(tx.getToWalletId(), tx.getAmount(), tx.getCurrency()));
      ledgerApi.post(tx.getId(), entries, "{\"type\": \"DEPOSIT\"}");
    } catch (RuntimeException e) {
      advance(tx, TransactionState.FAILED);
      metrics.recordFailure(SAGA, "PENDING", e.getClass().getSimpleName());
      eventPublisher.publishEvent(
          new TransferFailed(tx.getId(), TransactionState.PENDING, e.getMessage()));
      return;
    }

    advance(tx, TransactionState.POSTED);
    eventPublisher.publishEvent(new LedgerPosted(tx.getId()));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onLedgerPosted(LedgerPosted event) {
    Transaction tx = transactionRepository.findById(event.transactionId()).orElseThrow();
    if (tx.getType() != TransactionType.DEPOSIT) return;
    if (tx.getState() != TransactionState.POSTED) return;

    walletApi.credit(tx.getToWalletId(), tx.getId(), tx.getAmount());
    eventPublisher.publishEvent(new WalletCredited(tx.getId()));

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
