package com.wallet.transaction.event;

import java.math.BigDecimal;
import java.util.UUID;

public record WithdrawalRequested(
    UUID transactionId, UUID fromWalletId, BigDecimal amount, String currency) {}
