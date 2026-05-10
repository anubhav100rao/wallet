package com.wallet.ledger.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Public interface for the Ledger context. */
public interface LedgerApi {

  /**
   * Posts a balanced transaction to the ledger.
   *
   * @param transactionId the idempotency key for this ledger posting (usually the saga
   *     transactionId)
   * @param entries the legs of the transaction
   * @param metadata optional JSON metadata
   * @throws IllegalArgumentException if the entries do not sum to zero or accounts don't exist
   */
  void post(UUID transactionId, List<LedgerEntry> entries, String metadata);

  record LedgerEntry(UUID accountId, BigDecimal amount, String currency) {}
}
