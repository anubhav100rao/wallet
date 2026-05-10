package com.wallet.ledger.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.wallet.ledger.domain.Account;
import com.wallet.ledger.domain.AccountType;
import com.wallet.ledger.domain.JournalEntry;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("test")
class JournalEntryRepositorySliceTest {

  @Autowired private AccountRepository accountRepository;
  @Autowired private JournalEntryRepository journalEntryRepository;

  @Test
  void existsByTransactionIdReportsKnownTx() {
    Account a = saveAccount();
    Account b = saveAccount();
    UUID txId = UUID.randomUUID();
    journalEntryRepository.save(new JournalEntry(txId, a, new BigDecimal("-50.0000"), "USD", "{}"));
    journalEntryRepository.save(new JournalEntry(txId, b, new BigDecimal("50.0000"), "USD", "{}"));

    assertThat(journalEntryRepository.existsByTransactionId(txId)).isTrue();
    assertThat(journalEntryRepository.existsByTransactionId(UUID.randomUUID())).isFalse();
  }

  @Test
  void findByTransactionIdReturnsBothLegs() {
    Account a = saveAccount();
    Account b = saveAccount();
    UUID txId = UUID.randomUUID();
    journalEntryRepository.save(new JournalEntry(txId, a, new BigDecimal("-50.0000"), "USD", "{}"));
    journalEntryRepository.save(new JournalEntry(txId, b, new BigDecimal("50.0000"), "USD", "{}"));

    List<JournalEntry> entries = journalEntryRepository.findByTransactionId(txId);
    assertThat(entries).hasSize(2);
    BigDecimal sum =
        entries.stream().map(JournalEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(sum).isEqualByComparingTo(BigDecimal.ZERO);
  }

  // The DB-level deferred zero-sum trigger (V4) fires at COMMIT, which @DataJpaTest's auto-rollback
  // never reaches. The service-level guard in LedgerService.post is asserted end-to-end by
  // LedgerFuzzTest#rejectsUnbalancedPosting.

  private Account saveAccount() {
    return accountRepository.save(
        new Account(UUID.randomUUID(), UUID.randomUUID(), AccountType.USER_CASH, "USD"));
  }
}
