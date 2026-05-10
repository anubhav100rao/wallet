package com.wallet.transaction.event;

import java.util.UUID;

public record WalletCaptured(UUID transactionId) {}
