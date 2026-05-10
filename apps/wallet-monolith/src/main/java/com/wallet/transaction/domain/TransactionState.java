package com.wallet.transaction.domain;

public enum TransactionState {
  PENDING,
  HELD,
  POSTED,
  SETTLED,
  FAILED,
  COMPENSATED
}
