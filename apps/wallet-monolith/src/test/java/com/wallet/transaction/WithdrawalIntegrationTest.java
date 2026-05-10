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
import com.wallet.transaction.controller.WithdrawalController.WithdrawalRequest;
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
class WithdrawalIntegrationTest {

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
  void successfulWithdrawalSettlesWalletAndBalancesLedger() throws Exception {
    UUID userId = UUID.randomUUID();
    Wallet wallet = new Wallet(userId, "USD");
    wallet.creditTotalAndAvailable(new BigDecimal("500.0000"));
    wallet = walletRepository.save(wallet);
    accountRepository.save(new Account(wallet.getId(), userId, AccountType.USER_CASH, "USD"));

    WithdrawalRequest req =
        new WithdrawalRequest(wallet.getId(), new BigDecimal("120.0000"), "USD");
    String idempotencyKey = UUID.randomUUID().toString();

    MvcResult result =
        mockMvc
            .perform(
                post("/api/withdrawals")
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
    assertThat(updated.getTotalBalance()).isEqualByComparingTo("380");
    assertThat(updated.getAvailableBalance()).isEqualByComparingTo("380");

    // Hold should be CAPTURED, not active
    var hold =
        walletHoldRepository.findByWalletIdAndTransactionId(wallet.getId(), txId).orElseThrow();
    assertThat(hold.getState()).isEqualTo(HoldState.CAPTURED);

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
        .contains(
            "withdrawal.requested",
            "wallet.hold_placed",
            "ledger.posted",
            "wallet.captured",
            "transfer.completed");
  }

  @Test
  void withdrawalFailsAndCompensatesWhenLedgerAccountMissing() throws Exception {
    // Wallet has funds, hold succeeds, but no USER_CASH ledger account → ledger.post throws,
    // saga must release the hold and land in COMPENSATED.
    UUID userId = UUID.randomUUID();
    Wallet wallet = new Wallet(userId, "USD");
    wallet.creditTotalAndAvailable(new BigDecimal("500.0000"));
    wallet = walletRepository.save(wallet);
    // Intentionally NOT creating the user_cash account.

    WithdrawalRequest req =
        new WithdrawalRequest(wallet.getId(), new BigDecimal("100.0000"), "USD");
    String idempotencyKey = UUID.randomUUID().toString();

    MvcResult result =
        mockMvc
            .perform(
                post("/api/withdrawals")
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

    // Wallet must be whole again — held funds released.
    Wallet updated = walletRepository.findById(wallet.getId()).orElseThrow();
    assertThat(updated.getTotalBalance()).isEqualByComparingTo("500");
    assertThat(updated.getAvailableBalance()).isEqualByComparingTo("500");

    var hold =
        walletHoldRepository.findByWalletIdAndTransactionId(wallet.getId(), txId).orElseThrow();
    assertThat(hold.getState()).isEqualTo(HoldState.RELEASED);

    assertThat(journalEntryRepository.findByTransactionId(txId)).isEmpty();

    assertThat(outboxEventRepository.findAll())
        .extracting("eventType")
        .contains(
            "withdrawal.requested", "wallet.hold_placed", "wallet.hold_released", "transfer.failed")
        .doesNotContain("ledger.posted", "transfer.completed");
  }

  @Test
  void withdrawalFailsImmediatelyOnInsufficientFunds() throws Exception {
    UUID userId = UUID.randomUUID();
    Wallet wallet = walletRepository.save(new Wallet(userId, "USD")); // zero balance
    accountRepository.save(new Account(wallet.getId(), userId, AccountType.USER_CASH, "USD"));

    WithdrawalRequest req =
        new WithdrawalRequest(wallet.getId(), new BigDecimal("100.0000"), "USD");
    String idempotencyKey = UUID.randomUUID().toString();

    MvcResult result =
        mockMvc
            .perform(
                post("/api/withdrawals")
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
    assertThat(tx.getState()).isEqualTo(TransactionState.FAILED);

    Wallet updated = walletRepository.findById(wallet.getId()).orElseThrow();
    assertThat(updated.getTotalBalance()).isEqualByComparingTo("0");
    assertThat(updated.getAvailableBalance()).isEqualByComparingTo("0");

    assertThat(walletHoldRepository.findByWalletIdAndTransactionId(wallet.getId(), txId)).isEmpty();
  }
}
