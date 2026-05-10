package com.wallet.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.ledger.api.SystemAccounts;
import com.wallet.ledger.domain.Account;
import com.wallet.ledger.domain.AccountType;
import com.wallet.ledger.repository.AccountRepository;
import com.wallet.ledger.repository.JournalEntryRepository;
import com.wallet.shared.outbox.OutboxEventRepository;
import com.wallet.transaction.controller.DepositController.DepositRequest;
import com.wallet.transaction.domain.Transaction;
import com.wallet.transaction.domain.TransactionState;
import com.wallet.transaction.repository.TransactionRepository;
import com.wallet.wallet.domain.Wallet;
import com.wallet.wallet.repository.WalletCreditRepository;
import com.wallet.wallet.repository.WalletHoldRepository;
import com.wallet.wallet.repository.WalletRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class DepositIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Autowired private WalletRepository walletRepository;
  @Autowired private WalletHoldRepository walletHoldRepository;
  @Autowired private WalletCreditRepository walletCreditRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private JournalEntryRepository journalEntryRepository;
  @Autowired private TransactionRepository transactionRepository;
  @Autowired private OutboxEventRepository outboxEventRepository;

  @BeforeEach
  void setUp() {
    transactionRepository.deleteAllInBatch();
    outboxEventRepository.deleteAllInBatch();
    walletCreditRepository.deleteAllInBatch();
    walletHoldRepository.deleteAllInBatch();
    journalEntryRepository.deleteAllInBatch();
    // Don't wipe accounts — system_cash accounts are seeded by Flyway and must survive.
    accountRepository.deleteAll(
        accountRepository.findAll().stream()
            .filter(a -> a.getType() != AccountType.SYSTEM_CASH)
            .toList());
    walletRepository.deleteAllInBatch();
  }

  @Test
  void successfulDepositSettlesWalletAndBalancesLedger() throws Exception {
    UUID userId = UUID.randomUUID();
    Wallet wallet = walletRepository.save(new Wallet(userId, "USD"));
    accountRepository.save(new Account(wallet.getId(), userId, AccountType.USER_CASH, "USD"));

    DepositRequest req = new DepositRequest(wallet.getId(), new BigDecimal("250.0000"), "USD");
    String idempotencyKey = UUID.randomUUID().toString();

    MvcResult result =
        mockMvc
            .perform(
                post("/api/deposits")
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andReturn();

    UUID txId =
        UUID.fromString(
            objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("transactionId")
                .asText());

    Transaction tx = transactionRepository.findById(txId).orElseThrow();
    assertThat(tx.getState()).isEqualTo(TransactionState.SETTLED);

    Wallet updated = walletRepository.findById(wallet.getId()).orElseThrow();
    assertThat(updated.getTotalBalance()).isEqualByComparingTo("250");
    assertThat(updated.getAvailableBalance()).isEqualByComparingTo("250");

    var entries = journalEntryRepository.findByTransactionId(txId);
    assertThat(entries).hasSize(2);
    BigDecimal sum =
        entries.stream().map(e -> e.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(sum).isEqualByComparingTo(BigDecimal.ZERO);

    UUID systemCash = SystemAccounts.systemCash("USD");
    assertThat(entries.stream().map(e -> e.getAccount().getId()).toList())
        .containsExactlyInAnyOrder(systemCash, wallet.getId());

    assertThat(outboxEventRepository.findAll())
        .extracting("eventType")
        .contains("deposit.requested", "ledger.posted", "wallet.credited", "transfer.completed");
  }

  @Test
  void depositFailsCleanlyWhenLedgerAccountMissing() throws Exception {
    UUID userId = UUID.randomUUID();
    Wallet wallet = walletRepository.save(new Wallet(userId, "USD"));
    // Intentionally NOT creating the matching ledger account → ledger.post will throw.

    DepositRequest req = new DepositRequest(wallet.getId(), new BigDecimal("100.0000"), "USD");
    String idempotencyKey = UUID.randomUUID().toString();

    MvcResult result =
        mockMvc
            .perform(
                post("/api/deposits")
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andReturn();

    UUID txId =
        UUID.fromString(
            objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("transactionId")
                .asText());

    Transaction tx = transactionRepository.findById(txId).orElseThrow();
    // Pre-POSTED failure for a deposit (no hold to release) lands in FAILED.
    assertThat(tx.getState()).isEqualTo(TransactionState.FAILED);

    Wallet updated = walletRepository.findById(wallet.getId()).orElseThrow();
    assertThat(updated.getTotalBalance()).isEqualByComparingTo("0");

    List<?> entries = journalEntryRepository.findByTransactionId(txId);
    assertThat(entries).isEmpty();

    assertThat(outboxEventRepository.findAll())
        .extracting("eventType")
        .contains("deposit.requested", "transfer.failed")
        .doesNotContain("ledger.posted", "wallet.credited", "transfer.completed");
  }
}
