package com.wallet.transaction.event;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequested(
    UUID transactionId, UUID fromWalletId, UUID toWalletId, BigDecimal amount, String currency) {}
