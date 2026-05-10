package com.wallet.wallet.api;

import java.math.BigDecimal;
import java.util.UUID;

/** Public interface for the Wallet context. */
public interface WalletApi {

  UUID placeHold(UUID walletId, UUID transactionId, BigDecimal amount);

  void releaseHold(UUID holdId);

  void capture(UUID holdId);

  void captureByTransactionId(UUID walletId, UUID transactionId);

  void credit(UUID walletId, UUID transactionId, BigDecimal amount);
}
