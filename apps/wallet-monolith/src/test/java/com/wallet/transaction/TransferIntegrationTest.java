package com.wallet.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.ledger.domain.Account;
import com.wallet.ledger.domain.AccountType;
import com.wallet.ledger.repository.AccountRepository;
import com.wallet.ledger.repository.JournalEntryRepository;
import com.wallet.shared.outbox.OutboxEventRepository;
import com.wallet.transaction.controller.TransactionController.TransferRequest;
import com.wallet.transaction.domain.Transaction;
import com.wallet.transaction.domain.TransactionState;
import com.wallet.transaction.repository.TransactionRepository;
import com.wallet.wallet.WalletInvariants;
import com.wallet.wallet.domain.Wallet;
import com.wallet.wallet.repository.WalletCreditRepository;
import com.wallet.wallet.repository.WalletHoldRepository;
import com.wallet.wallet.repository.WalletRepository;
import java.math.BigDecimal;
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
@AutoConfigureMockMvc(addFilters = false) // Disable security for this test
@ActiveProfiles("test")
class TransferIntegrationTest {

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
    // Preserve SYSTEM_CASH accounts seeded by Flyway V8 — deposits/withdrawals depend on them.
    accountRepository.deleteAll(
        accountRepository.findAll().stream()
            .filter(a -> a.getType() != AccountType.SYSTEM_CASH)
            .toList());
    walletRepository.deleteAllInBatch();
  }

  @Test
  void testSuccessfulTransferSaga() throws Exception {
    // 1. Setup Source Wallet with 1000 USD
    UUID user1 = UUID.randomUUID();
    Wallet w1 = new Wallet(user1, "USD");
    w1.creditTotalAndAvailable(new BigDecimal("1000.0000"));
    w1 = walletRepository.save(w1);

    // Create corresponding ledger account
    Account a1 = new Account(w1.getId(), user1, AccountType.USER_CASH, "USD");
    accountRepository.save(a1);

    // 2. Setup Destination Wallet with 0 USD
    UUID user2 = UUID.randomUUID();
    Wallet w2 = new Wallet(user2, "USD");
    w2 = walletRepository.save(w2);

    Account a2 = new Account(w2.getId(), user2, AccountType.USER_CASH, "USD");
    accountRepository.save(a2);

    // 3. Initiate Transfer of 100 USD
    TransferRequest req =
        new TransferRequest(w1.getId(), w2.getId(), new BigDecimal("100.0000"), "USD");
    String idempotencyKey = UUID.randomUUID().toString();

    MvcResult result =
        mockMvc
            .perform(
                post("/api/transfers")
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andReturn();

    String responseBody = result.getResponse().getContentAsString();
    String txIdStr = objectMapper.readTree(responseBody).get("transactionId").asText();
    UUID txId = UUID.fromString(txIdStr);

    // 4. Verify Saga Completion (Since events are synchronous in Phase 1, it should be SETTLED
    // immediately)
    Transaction tx = transactionRepository.findById(txId).orElseThrow();
    assertThat(tx.getState()).isEqualTo(TransactionState.SETTLED);

    // 5. Verify Wallet Balances
    Wallet updatedW1 = walletRepository.findById(w1.getId()).orElseThrow();
    assertThat(updatedW1.getTotalBalance()).isEqualByComparingTo("900");
    assertThat(updatedW1.getAvailableBalance()).isEqualByComparingTo("900");

    Wallet updatedW2 = walletRepository.findById(w2.getId()).orElseThrow();
    assertThat(updatedW2.getTotalBalance()).isEqualByComparingTo("100");
    assertThat(updatedW2.getAvailableBalance()).isEqualByComparingTo("100");

    // Hold invariant: after settlement no active holds remain on either side.
    WalletInvariants.assertHoldInvariant(walletRepository, walletHoldRepository, w1.getId());
    WalletInvariants.assertHoldInvariant(walletRepository, walletHoldRepository, w2.getId());

    // 6. Verify Ledger Entries
    var entries = journalEntryRepository.findByTransactionId(txId);
    assertThat(entries).hasSize(2);

    // Sum should be zero
    BigDecimal sum =
        entries.stream().map(e -> e.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(sum).isEqualByComparingTo(BigDecimal.ZERO);

    assertThat(outboxEventRepository.findAll())
        .extracting("eventType")
        .contains(
            "transfer.requested",
            "wallet.hold_placed",
            "ledger.posted",
            "wallet.captured",
            "wallet.credited",
            "transfer.completed");
  }
}
