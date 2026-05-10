package com.wallet.wallet.controller;

import com.wallet.wallet.domain.HoldState;
import com.wallet.wallet.domain.Wallet;
import com.wallet.wallet.domain.WalletHold;
import com.wallet.wallet.repository.WalletHoldRepository;
import com.wallet.wallet.repository.WalletRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallets")
public class WalletController {

  private final WalletRepository walletRepository;
  private final WalletHoldRepository walletHoldRepository;

  public WalletController(
      WalletRepository walletRepository, WalletHoldRepository walletHoldRepository) {
    this.walletRepository = walletRepository;
    this.walletHoldRepository = walletHoldRepository;
  }

  @GetMapping("/{id}")
  public ResponseEntity<WalletResponse> getWallet(@PathVariable UUID id) {
    return walletRepository
        .findById(id)
        .map(w -> ResponseEntity.ok(new WalletResponse(w)))
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/{id}/holds/active")
  public ResponseEntity<List<WalletHoldResponse>> getActiveHolds(@PathVariable UUID id) {
    // In a real system, we'd use a more specific query: findByWalletIdAndState
    List<WalletHoldResponse> holds =
        walletHoldRepository.findAll().stream()
            .filter(h -> h.getWallet().getId().equals(id) && h.getState() == HoldState.ACTIVE)
            .map(WalletHoldResponse::new)
            .toList();
    return ResponseEntity.ok(holds);
  }

  public record WalletResponse(
      UUID id, String currency, String totalBalance, String availableBalance) {
    public WalletResponse(Wallet w) {
      this(
          w.getId(),
          w.getCurrency(),
          w.getTotalBalance().toPlainString(),
          w.getAvailableBalance().toPlainString());
    }
  }

  public record WalletHoldResponse(UUID id, UUID transactionId, String amount, String expiresAt) {
    public WalletHoldResponse(WalletHold h) {
      this(
          h.getId(),
          h.getTransactionId(),
          h.getAmount().toPlainString(),
          h.getExpiresAt().toString());
    }
  }
}
