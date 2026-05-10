package com.wallet.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.ledger.domain.Account;
import com.wallet.ledger.domain.AccountType;
import com.wallet.ledger.repository.AccountRepository;
import com.wallet.ledger.repository.JournalEntryRepository;
import com.wallet.shared.outbox.OutboxEventRepository;
import com.wallet.transaction.repository.TransactionRepository;
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

/**
 * Contract tests pin the public HTTP surface: every endpoint, the status code it returns for the
 * happy path, and the shape of its response body (field names, not deep semantics). Renaming a
 * field, dropping a required key, or changing a status code surfaces here loudly.
 *
 * <p>OpenAPI spec generation is also smoke-tested so {@code /v3/api-docs} stays consumable.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class PublicApiContractTest {

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
  void clean() {
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

  // ── OpenAPI smoke ──────────────────────────────────────────────

  @Test
  void openApiSpecListsAllPublicEndpoints() throws Exception {
    String body =
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode spec = objectMapper.readTree(body);
    JsonNode paths = spec.get("paths");
    assertThat(paths).isNotNull();
    assertThat(paths.fieldNames())
        .toIterable()
        .contains(
            "/api/transfers",
            "/api/deposits",
            "/api/withdrawals",
            "/auth/register",
            "/auth/login",
            "/auth/refresh",
            "/auth/logout",
            "/kyc/submit");
  }

  // ── Auth ──────────────────────────────────────────────────────

  @Test
  void registerReturnsAccessAndRefreshTokens() throws Exception {
    String email = "contract-" + UUID.randomUUID() + "@wallet.com";
    String body = String.format("{\"email\":\"%s\",\"password\":\"password123\"}", email);
    mockMvc
        .perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isString())
        .andExpect(jsonPath("$.refreshToken").isString());
  }

  @Test
  void loginRejectsBadCredentialsAsBadRequest() throws Exception {
    String body = "{\"email\":\"unknown@wallet.com\",\"password\":\"wrong-password\"}";
    mockMvc
        .perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().is4xxClientError());
  }

  // ── Transfer ──────────────────────────────────────────────────

  @Test
  void transferReturnsTransactionIdAndStatus() throws Exception {
    UUID u1 = UUID.randomUUID();
    Wallet w1 = new Wallet(u1, "USD");
    w1.creditTotalAndAvailable(new BigDecimal("100.0000"));
    w1 = walletRepository.save(w1);
    accountRepository.save(new Account(w1.getId(), u1, AccountType.USER_CASH, "USD"));

    UUID u2 = UUID.randomUUID();
    Wallet w2 = walletRepository.save(new Wallet(u2, "USD"));
    accountRepository.save(new Account(w2.getId(), u2, AccountType.USER_CASH, "USD"));

    String body =
        String.format(
            "{\"fromWalletId\":\"%s\",\"toWalletId\":\"%s\",\"amount\":10,\"currency\":\"USD\"}",
            w1.getId(), w2.getId());

    mockMvc
        .perform(
            post("/api/transfers")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactionId").isString())
        .andExpect(jsonPath("$.status").isString());
  }

  @Test
  void transferRejectsMissingIdempotencyKeyAsBadRequest() throws Exception {
    String body =
        String.format(
            "{\"fromWalletId\":\"%s\",\"toWalletId\":\"%s\",\"amount\":1,\"currency\":\"USD\"}",
            UUID.randomUUID(), UUID.randomUUID());
    mockMvc
        .perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  // ── Deposit & Withdrawal ──────────────────────────────────────

  @Test
  void depositReturnsTransactionIdAndStatus() throws Exception {
    UUID userId = UUID.randomUUID();
    Wallet w = walletRepository.save(new Wallet(userId, "USD"));
    accountRepository.save(new Account(w.getId(), userId, AccountType.USER_CASH, "USD"));

    String body =
        String.format("{\"toWalletId\":\"%s\",\"amount\":50,\"currency\":\"USD\"}", w.getId());
    mockMvc
        .perform(
            post("/api/deposits")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactionId").isString())
        .andExpect(jsonPath("$.status").isString());
  }

  @Test
  void withdrawalReturnsTransactionIdAndStatus() throws Exception {
    UUID userId = UUID.randomUUID();
    Wallet w = new Wallet(userId, "USD");
    w.creditTotalAndAvailable(new BigDecimal("500.0000"));
    w = walletRepository.save(w);
    accountRepository.save(new Account(w.getId(), userId, AccountType.USER_CASH, "USD"));

    String body =
        String.format("{\"fromWalletId\":\"%s\",\"amount\":25,\"currency\":\"USD\"}", w.getId());
    mockMvc
        .perform(
            post("/api/withdrawals")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactionId").isString())
        .andExpect(jsonPath("$.status").isString());
  }

  // ── Errors return RFC 7807 ProblemDetail shape ────────────────

  @Test
  void notFoundResponsesUseProblemDetailShape() throws Exception {
    mockMvc
        .perform(get("/api/this-endpoint-does-not-exist"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").isString())
        .andExpect(jsonPath("$.title").value("not_found"))
        .andExpect(jsonPath("$.status").value(404));
  }
}
