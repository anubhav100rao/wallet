package com.wallet.transaction.service;

import com.wallet.transaction.domain.Transaction;
import com.wallet.transaction.domain.TransactionType;
import com.wallet.transaction.event.DepositRequested;
import com.wallet.transaction.event.TransferRequested;
import com.wallet.transaction.event.WithdrawalRequested;
import com.wallet.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

  private final TransactionRepository transactionRepository;
  private final ApplicationEventPublisher eventPublisher;

  public TransactionService(
      TransactionRepository transactionRepository, ApplicationEventPublisher eventPublisher) {
    this.transactionRepository = transactionRepository;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public Transaction requestTransfer(
      String idempotencyKey,
      UUID fromWalletId,
      UUID toWalletId,
      BigDecimal amount,
      String currency) {
    Transaction tx =
        persist(
            new Transaction(
                TransactionType.P2P_TRANSFER,
                idempotencyKey,
                fromWalletId,
                toWalletId,
                amount,
                currency),
            idempotencyKey);

    eventPublisher.publishEvent(
        new TransferRequested(
            tx.getId(),
            tx.getFromWalletId(),
            tx.getToWalletId(),
            tx.getAmount(),
            tx.getCurrency()));

    return tx;
  }

  @Transactional
  public Transaction requestDeposit(
      String idempotencyKey, UUID toWalletId, BigDecimal amount, String currency) {
    Transaction tx =
        persist(
            new Transaction(
                TransactionType.DEPOSIT, idempotencyKey, null, toWalletId, amount, currency),
            idempotencyKey);

    eventPublisher.publishEvent(new DepositRequested(tx.getId(), toWalletId, amount, currency));

    return tx;
  }

  @Transactional
  public Transaction requestWithdrawal(
      String idempotencyKey, UUID fromWalletId, BigDecimal amount, String currency) {
    Transaction tx =
        persist(
            new Transaction(
                TransactionType.WITHDRAWAL, idempotencyKey, fromWalletId, null, amount, currency),
            idempotencyKey);

    eventPublisher.publishEvent(
        new WithdrawalRequested(tx.getId(), fromWalletId, amount, currency));

    return tx;
  }

  private Transaction persist(Transaction tx, String idempotencyKey) {
    try {
      return transactionRepository.saveAndFlush(tx);
    } catch (DataIntegrityViolationException e) {
      throw new IllegalArgumentException(
          "Transaction with idempotency key already exists: " + idempotencyKey);
    }
  }
}
