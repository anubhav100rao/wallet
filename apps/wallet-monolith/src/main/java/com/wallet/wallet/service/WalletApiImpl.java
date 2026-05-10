package com.wallet.wallet.service;

import com.wallet.wallet.api.WalletApi;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WalletApiImpl implements WalletApi {

  private final WalletService walletService;

  public WalletApiImpl(WalletService walletService) {
    this.walletService = walletService;
  }

  @Override
  public UUID placeHold(UUID walletId, UUID transactionId, BigDecimal amount) {
    return walletService.placeHold(walletId, transactionId, amount);
  }

  @Override
  public void releaseHold(UUID holdId) {
    walletService.releaseHold(holdId);
  }

  @Override
  public void capture(UUID holdId) {
    walletService.capture(holdId);
  }

  @Override
  public void captureByTransactionId(UUID walletId, UUID transactionId) {
    walletService.captureByTransactionId(walletId, transactionId);
  }

  @Override
  public void credit(UUID walletId, UUID transactionId, BigDecimal amount) {
    walletService.credit(walletId, transactionId, amount);
  }
}
