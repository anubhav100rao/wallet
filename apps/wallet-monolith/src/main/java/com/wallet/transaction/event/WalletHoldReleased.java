package com.wallet.transaction.event;

import java.util.UUID;

public record WalletHoldReleased(UUID transactionId, UUID holdId, String reason) {}
