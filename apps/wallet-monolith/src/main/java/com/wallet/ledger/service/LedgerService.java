package com.wallet.ledger.service;

import com.wallet.ledger.domain.Account;
import com.wallet.ledger.domain.JournalEntry;
import com.wallet.ledger.repository.AccountRepository;
import com.wallet.ledger.repository.JournalEntryRepository;
import com.wallet.shared.money.CurrencyMismatchException;
import com.wallet.shared.observability.BankingMetrics;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {

  private final AccountRepository accountRepository;
  private final JournalEntryRepository journalEntryRepository;
  private final BankingMetrics metrics;

  public LedgerService(
      AccountRepository accountRepository,
      JournalEntryRepository journalEntryRepository,
      BankingMetrics metrics) {
    this.accountRepository = accountRepository;
    this.journalEntryRepository = journalEntryRepository;
    this.metrics = metrics;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void post(UUID transactionId, List<PostEntryCommand> entries, String metadata) {
    long start = System.nanoTime();
    String outcome = "ok";
    try {
      if (journalEntryRepository.existsByTransactionId(transactionId)) {
        outcome = "duplicate";
        return;
      }

      if (entries.isEmpty()) {
        throw new IllegalArgumentException("Cannot post empty journal entries");
      }

      String currency = entries.getFirst().currency();
      BigDecimal sum = BigDecimal.ZERO;
      List<JournalEntry> journalEntries = new ArrayList<>();

      for (PostEntryCommand cmd : entries) {
        if (!cmd.currency().equals(currency)) {
          throw new CurrencyMismatchException(
              com.wallet.shared.money.Currency.fromCode(currency),
              com.wallet.shared.money.Currency.fromCode(cmd.currency()));
        }
        sum = sum.add(cmd.amount());

        Account account =
            accountRepository
                .findById(cmd.accountId())
                .orElseThrow(
                    () -> new IllegalArgumentException("Account not found: " + cmd.accountId()));

        journalEntries.add(
            new JournalEntry(transactionId, account, cmd.amount(), currency, metadata));
      }

      if (sum.compareTo(BigDecimal.ZERO) != 0) {
        throw new IllegalArgumentException("Journal entries do not sum to zero. Sum: " + sum);
      }

      journalEntryRepository.saveAll(journalEntries);
    } catch (RuntimeException e) {
      outcome = "error";
      throw e;
    } finally {
      metrics.recordLedgerPost(outcome, Duration.ofNanos(System.nanoTime() - start));
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void reverse(UUID originalTransactionId, UUID reversalTransactionId, String metadata) {
    if (journalEntryRepository.existsByTransactionId(reversalTransactionId)) {
      return;
    }

    List<JournalEntry> originalEntries =
        journalEntryRepository.findByTransactionId(originalTransactionId);
    if (originalEntries.isEmpty()) {
      throw new IllegalArgumentException(
          "Original transaction not found: " + originalTransactionId);
    }

    List<JournalEntry> reversalEntries = new ArrayList<>();
    for (JournalEntry original : originalEntries) {
      reversalEntries.add(
          new JournalEntry(
              reversalTransactionId,
              original.getAccount(),
              original.getAmount().negate(),
              original.getCurrency(),
              metadata));
    }

    journalEntryRepository.saveAll(reversalEntries);
  }

  public record PostEntryCommand(UUID accountId, BigDecimal amount, String currency) {}
}
