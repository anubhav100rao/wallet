package com.wallet.transaction.event;

import java.util.UUID;

public record TransferCompleted(UUID transactionId) {}
