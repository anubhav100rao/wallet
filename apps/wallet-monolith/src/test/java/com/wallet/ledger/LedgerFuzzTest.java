package com.wallet.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wallet.ledger.domain.Account;
import com.wallet.ledger.domain.AccountType;
import com.wallet.ledger.domain.JournalEntry;
import com.wallet.ledger.repository.AccountRepository;
import com.wallet.ledger.repository.JournalEntryRepository;
import com.wallet.ledger.service.LedgerService;
import com.wallet.ledger.service.LedgerService.PostEntryCommand;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Property-style fuzz test: under N random balanced postings, the ledger zero-sum invariant must
 * hold (per-transaction and globally) and per-account balances must match the running tally.
 */
@SpringBootTest
@ActiveProfiles("test")
class LedgerFuzzTest {

  private static final int NUM_ACCOUNTS = 10;
  private static final int NUM_TRANSACTIONS = 1000;
  private static final long SEED = 0xB001CAFEL;

  @Autowired private LedgerService ledgerService;
  @Autowired private AccountRepository accountRepository;
  @Autowired private JournalEntryRepository journalEntryRepository;

  @BeforeEach
  void setUp() {
    journalEntryRepository.deleteAllInBatch();
    accountRepository.deleteAll(
        accountRepository.findAll().stream()
            .filter(a -> a.getType() != AccountType.SYSTEM_CASH)
            .toList());
  }

  @Test
  void invariantsHoldUnderThousandRandomTransactions() {
    Random rnd = new Random(SEED);
    List<Account> accounts = new ArrayList<>();
    for (int i = 0; i < NUM_ACCOUNTS; i++) {
      accounts.add(
          accountRepository.save(
              new Account(UUID.randomUUID(), UUID.randomUUID(), AccountType.USER_CASH, "USD")));
    }

    Map<UUID, BigDecimal> expectedBalance = new HashMap<>();
    for (Account a : accounts) expectedBalance.put(a.getId(), BigDecimal.ZERO);

    for (int i = 0; i < NUM_TRANSACTIONS; i++) {
      int srcIdx = rnd.nextInt(NUM_ACCOUNTS);
      int dstIdx;
      do {
        dstIdx = rnd.nextInt(NUM_ACCOUNTS);
      } while (dstIdx == srcIdx);

      // Random amount in cents to avoid scale drift; convert to NUMERIC(19,4).
      BigDecimal amount =
          new BigDecimal(1 + rnd.nextInt(100_000))
              .movePointLeft(2)
              .setScale(4, java.math.RoundingMode.UNNECESSARY);

      UUID txId = UUID.randomUUID();
      Account src = accounts.get(srcIdx);
      Account dst = accounts.get(dstIdx);

      ledgerService.post(
          txId,
          List.of(
              new PostEntryCommand(src.getId(), amount.negate(), "USD"),
              new PostEntryCommand(dst.getId(), amount, "USD")),
          "{}");

      expectedBalance.merge(src.getId(), amount.negate(), BigDecimal::add);
      expectedBalance.merge(dst.getId(), amount, BigDecimal::add);

      // Per-tx zero-sum check (DB also enforces this via deferred trigger).
      List<JournalEntry> txEntries = journalEntryRepository.findByTransactionId(txId);
      assertThat(txEntries).hasSize(2);
      assertThat(
              txEntries.stream()
                  .map(JournalEntry::getAmount)
                  .reduce(BigDecimal.ZERO, BigDecimal::add))
          .isEqualByComparingTo(BigDecimal.ZERO);
    }

    // Per-account balance matches the running tally.
    for (Account a : accounts) {
      BigDecimal computed =
          journalEntryRepository.findAll().stream()
              .filter(e -> e.getAccount().getId().equals(a.getId()))
              .map(JournalEntry::getAmount)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      assertThat(computed)
          .as("Computed balance for account %s should match running tally", a.getId())
          .isEqualByComparingTo(expectedBalance.get(a.getId()));
    }

    // Global signed sum across all entries (over the user accounts we created) is zero.
    BigDecimal globalSum =
        journalEntryRepository.findAll().stream()
            .filter(e -> expectedBalance.containsKey(e.getAccount().getId()))
            .map(JournalEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(globalSum).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void rejectsUnbalancedPosting() {
    Account a =
        accountRepository.save(
            new Account(UUID.randomUUID(), UUID.randomUUID(), AccountType.USER_CASH, "USD"));
    Account b =
        accountRepository.save(
            new Account(UUID.randomUUID(), UUID.randomUUID(), AccountType.USER_CASH, "USD"));

    assertThatThrownBy(
            () ->
                ledgerService.post(
                    UUID.randomUUID(),
                    List.of(
                        new PostEntryCommand(a.getId(), new BigDecimal("-100.0000"), "USD"),
                        new PostEntryCommand(b.getId(), new BigDecimal("99.0000"), "USD")),
                    "{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sum to zero");
  }

  @Test
  void rejectsCurrencyMismatchAcrossLegs() {
    Account a =
        accountRepository.save(
            new Account(UUID.randomUUID(), UUID.randomUUID(), AccountType.USER_CASH, "USD"));
    Account b =
        accountRepository.save(
            new Account(UUID.randomUUID(), UUID.randomUUID(), AccountType.USER_CASH, "EUR"));

    assertThatThrownBy(
            () ->
                ledgerService.post(
                    UUID.randomUUID(),
                    List.of(
                        new PostEntryCommand(a.getId(), new BigDecimal("-100.0000"), "USD"),
                        new PostEntryCommand(b.getId(), new BigDecimal("100.0000"), "EUR")),
                    "{}"))
        .isInstanceOf(com.wallet.shared.money.CurrencyMismatchException.class);
  }

  @Test
  void postIsIdempotentByTransactionId() {
    Account a =
        accountRepository.save(
            new Account(UUID.randomUUID(), UUID.randomUUID(), AccountType.USER_CASH, "USD"));
    Account b =
        accountRepository.save(
            new Account(UUID.randomUUID(), UUID.randomUUID(), AccountType.USER_CASH, "USD"));

    UUID txId = UUID.randomUUID();
    List<PostEntryCommand> entries =
        List.of(
            new PostEntryCommand(a.getId(), new BigDecimal("-50.0000"), "USD"),
            new PostEntryCommand(b.getId(), new BigDecimal("50.0000"), "USD"));

    ledgerService.post(txId, entries, "{}");
    ledgerService.post(txId, entries, "{}"); // second call must be a no-op

    assertThat(journalEntryRepository.findByTransactionId(txId)).hasSize(2);
  }
}
