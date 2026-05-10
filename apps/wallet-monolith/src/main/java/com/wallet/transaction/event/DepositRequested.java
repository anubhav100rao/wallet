package com.wallet.transaction.event;

import java.math.BigDecimal;
import java.util.UUID;

public record DepositRequested(
    UUID transactionId, UUID toWalletId, BigDecimal amount, String currency) {}
