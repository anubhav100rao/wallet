package com.wallet.shared.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.identity.api.UserKycVerified;
import com.wallet.identity.api.UserRegistered;
import com.wallet.transaction.event.DepositRequested;
import com.wallet.transaction.event.LedgerPosted;
import com.wallet.transaction.event.TransferCompleted;
import com.wallet.transaction.event.TransferFailed;
import com.wallet.transaction.event.TransferRequested;
import com.wallet.transaction.event.WalletCaptured;
import com.wallet.transaction.event.WalletCredited;
import com.wallet.transaction.event.WalletHoldExpired;
import com.wallet.transaction.event.WalletHoldPlaced;
import com.wallet.transaction.event.WalletHoldReleased;
import com.wallet.transaction.event.WithdrawalRequested;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OutboxEventWriter {

  private final OutboxEventRepository repository;
  private final ObjectMapper objectMapper;

  public OutboxEventWriter(OutboxEventRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void on(UserRegistered event) {
    save("identity", event.userId(), "user.registered", event);
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void on(UserKycVerified event) {
    save("identity", event.userId(), "user.kyc_verified", event);
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void on(TransferRequested event) {
    save("transaction", event.transactionId(), "transfer.requested", event);
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void on(DepositRequested event) {
    save("transaction", event.transactionId(), "deposit.requested", event);
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void on(WithdrawalRequested event) {
    save("transaction", event.transactionId(), "withdrawal.requested", event);
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void on(WalletHoldPlaced event) {
    save("wallet", event.transactionId(), "wallet.hold_placed", event);
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void on(LedgerPosted event) {
    save("ledger", event.transactionId(), "ledger.posted", event);
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void on(WalletCaptured event) {
    save("wallet", event.transactionId(), "wallet.captured", event);
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void on(WalletCredited event) {
    save("wallet", event.transactionId(), "wallet.credited", event);
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void on(WalletHoldExpired event) {
    save("wallet", event.holdId(), "wallet.hold_expired", event);
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void on(WalletHoldReleased event) {
    save("wallet", event.transactionId(), "wallet.hold_released", event);
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void on(TransferCompleted event) {
    save("transaction", event.transactionId(), "transfer.completed", event);
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void on(TransferFailed event) {
    save("transaction", event.transactionId(), "transfer.failed", event);
  }

  private void save(String aggregateType, UUID aggregateId, String eventType, Object event) {
    try {
      repository.save(
          new OutboxEvent(
              aggregateType, aggregateId, eventType, objectMapper.writeValueAsString(event), "{}"));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize outbox event " + eventType, e);
    }
  }
}
