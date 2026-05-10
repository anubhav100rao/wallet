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
import com.wallet.wallet.domain.HoldState;
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
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class TransferCompensationTest {

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
    accountRepository.deleteAll(
        accountRepository.findAll().stream()
            .filter(a -> a.getType() != AccountType.SYSTEM_CASH)
            .toList());
    walletRepository.deleteAllInBatch();
  }

  @Test
  void transferCompensatesWhenLedgerPostFails() throws Exception {
    // Source wallet funded; source ledger account exists; destination ledger account is missing
    // → ledger.post will throw → saga must release the hold and land in COMPENSATED.
    UUID user1 = UUID.randomUUID();
    Wallet w1 = new Wallet(user1, "USD");
    w1.creditTotalAndAvailable(new BigDecimal("1000.0000"));
    w1 = walletRepository.save(w1);
    accountRepository.save(new Account(w1.getId(), user1, AccountType.USER_CASH, "USD"));

    UUID user2 = UUID.randomUUID();
    Wallet w2 = walletRepository.save(new Wallet(user2, "USD"));
    // No ledger account for w2 → second leg of ledger.post will fail.

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

    UUID txId =
        UUID.fromString(
            objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("transactionId")
                .asText());

    Transaction tx = transactionRepository.findById(txId).orElseThrow();
    assertThat(tx.getState()).isEqualTo(TransactionState.COMPENSATED);

    Wallet updatedW1 = walletRepository.findById(w1.getId()).orElseThrow();
    assertThat(updatedW1.getTotalBalance()).isEqualByComparingTo("1000");
    assertThat(updatedW1.getAvailableBalance()).isEqualByComparingTo("1000");

    Wallet updatedW2 = walletRepository.findById(w2.getId()).orElseThrow();
    assertThat(updatedW2.getTotalBalance()).isEqualByComparingTo("0");

    var hold = walletHoldRepository.findByWalletIdAndTransactionId(w1.getId(), txId).orElseThrow();
    assertThat(hold.getState()).isEqualTo(HoldState.RELEASED);

    assertThat(journalEntryRepository.findByTransactionId(txId)).isEmpty();

    assertThat(outboxEventRepository.findAll())
        .extracting("eventType")
        .contains(
            "transfer.requested", "wallet.hold_placed", "wallet.hold_released", "transfer.failed")
        .doesNotContain(
            "ledger.posted", "wallet.captured", "wallet.credited", "transfer.completed");
  }

  @Test
  void transferFailsImmediatelyWhenSourceWalletMissing() throws Exception {
    UUID user2 = UUID.randomUUID();
    Wallet w2 = walletRepository.save(new Wallet(user2, "USD"));
    accountRepository.save(new Account(w2.getId(), user2, AccountType.USER_CASH, "USD"));

    UUID nonexistentSource = UUID.randomUUID();
    TransferRequest req =
        new TransferRequest(nonexistentSource, w2.getId(), new BigDecimal("100.0000"), "USD");
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

    UUID txId =
        UUID.fromString(
            objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("transactionId")
                .asText());

    Transaction tx = transactionRepository.findById(txId).orElseThrow();
    // Pre-HELD failure (no hold ever placed) → FAILED, not COMPENSATED.
    assertThat(tx.getState()).isEqualTo(TransactionState.FAILED);

    assertThat(walletHoldRepository.findByWalletIdAndTransactionId(nonexistentSource, txId))
        .isEmpty();
    assertThat(journalEntryRepository.findByTransactionId(txId)).isEmpty();

    assertThat(outboxEventRepository.findAll())
        .extracting("eventType")
        .contains("transfer.requested", "transfer.failed")
        .doesNotContain("wallet.hold_placed", "ledger.posted", "transfer.completed");
  }
}
