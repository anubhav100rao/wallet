package com.wallet.transaction.event;

import java.util.UUID;

public record WalletHoldPlaced(UUID transactionId, UUID holdId) {}
