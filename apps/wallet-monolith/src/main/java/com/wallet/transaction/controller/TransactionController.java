package com.wallet.transaction.controller;

import com.wallet.shared.idempotency.Idempotent;
import com.wallet.transaction.domain.Transaction;
import com.wallet.transaction.service.TransactionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
public class TransactionController {

  private final TransactionService transactionService;

  public TransactionController(TransactionService transactionService) {
    this.transactionService = transactionService;
  }

  @PostMapping
  @Idempotent
  public ResponseEntity<TransferResponse> createTransfer(
      @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
      @Valid @RequestBody TransferRequest req) {

    Transaction tx =
        transactionService.requestTransfer(
            idempotencyKey, req.fromWalletId(), req.toWalletId(), req.amount(), req.currency());

    return ResponseEntity.ok(new TransferResponse(tx.getId(), tx.getState().name()));
  }

  public record TransferRequest(
      @NotNull UUID fromWalletId,
      @NotNull UUID toWalletId,
      @NotNull @Positive BigDecimal amount,
      @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO code")
          String currency) {}

  public record TransferResponse(UUID transactionId, String status) {}
}
