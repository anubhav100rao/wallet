package com.wallet.transaction;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class RequestValidationTest {

  @Autowired private MockMvc mockMvc;

  // ── Transfer ───────────────────────────────────────────────────

  @Test
  void transferRejectsNegativeAmount() throws Exception {
    String body =
        String.format(
            """
            {"fromWalletId":"%s","toWalletId":"%s","amount":-1,"currency":"USD"}
            """,
            UUID.randomUUID(), UUID.randomUUID());
    mockMvc
        .perform(
            post("/api/transfers")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("validation_failed"))
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("amount")));
  }

  @Test
  void transferRejectsBadCurrencyCode() throws Exception {
    String body =
        String.format(
            """
            {"fromWalletId":"%s","toWalletId":"%s","amount":100,"currency":"dollars"}
            """,
            UUID.randomUUID(), UUID.randomUUID());
    mockMvc
        .perform(
            post("/api/transfers")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("validation_failed"));
  }

  @Test
  void transferRejectsMissingFromWallet() throws Exception {
    String body =
        String.format(
            """
            {"toWalletId":"%s","amount":100,"currency":"USD"}
            """,
            UUID.randomUUID());
    mockMvc
        .perform(
            post("/api/transfers")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("validation_failed"));
  }

  @Test
  void transferRejectsMalformedJson() throws Exception {
    mockMvc
        .perform(
            post("/api/transfers")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ this is not json }"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("malformed_request"));
  }

  // ── Deposit / Withdrawal ───────────────────────────────────────

  @Test
  void depositRejectsZeroAmount() throws Exception {
    String body =
        String.format(
            """
            {"toWalletId":"%s","amount":0,"currency":"USD"}
            """,
            UUID.randomUUID());
    mockMvc
        .perform(
            post("/api/deposits")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("validation_failed"));
  }

  @Test
  void withdrawalRejectsBlankCurrency() throws Exception {
    String body =
        String.format(
            """
            {"fromWalletId":"%s","amount":100,"currency":""}
            """,
            UUID.randomUUID());
    mockMvc
        .perform(
            post("/api/withdrawals")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("validation_failed"));
  }
}
