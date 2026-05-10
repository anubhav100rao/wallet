package com.wallet.transaction.event;

import com.wallet.transaction.domain.TransactionState;
import java.util.UUID;

public record TransferFailed(UUID transactionId, TransactionState atState, String reason) {}
