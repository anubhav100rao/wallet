package com.wallet.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions", schema = "transaction")
public class Transaction {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false)
  private TransactionType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TransactionState state;

  @Column(name = "idempotency_key", unique = true, updatable = false)
  private String idempotencyKey;

  @Column(name = "from_wallet_id", updatable = false)
  private UUID fromWalletId;

  @Column(name = "to_wallet_id", updatable = false)
  private UUID toWalletId;

  @Column(nullable = false, updatable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  @Column(nullable = false, length = 3, updatable = false)
  private String currency;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected Transaction() {}

  public Transaction(
      TransactionType type,
      String idempotencyKey,
      UUID fromWalletId,
      UUID toWalletId,
      BigDecimal amount,
      String currency) {
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Transaction amount must be positive");
    }
    this.type = type;
    this.idempotencyKey = idempotencyKey;
    this.fromWalletId = fromWalletId;
    this.toWalletId = toWalletId;
    this.amount = amount;
    this.currency = currency;
    this.state = TransactionState.PENDING;
  }

  public UUID getId() {
    return id;
  }

  public TransactionType getType() {
    return type;
  }

  public TransactionState getState() {
    return state;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public UUID getFromWalletId() {
    return fromWalletId;
  }

  public UUID getToWalletId() {
    return toWalletId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void advanceState(TransactionState newState) {
    if (!isValidTransition(this.state, newState)) {
      throw new IllegalStateException(
          "Invalid transaction state transition: " + this.state + " -> " + newState);
    }
    this.state = newState;
    this.updatedAt = Instant.now();
  }

  /**
   * Strict FSM. Captures every transition any of the three sagas (transfer, deposit, withdrawal)
   * can take. Terminal states ({@code SETTLED}, {@code FAILED}, {@code COMPENSATED}) cannot be
   * left.
   */
  static boolean isValidTransition(TransactionState from, TransactionState to) {
    if (from == to) {
      return true; // idempotent re-entry
    }
    return switch (from) {
      case PENDING ->
          to == TransactionState.HELD
              || to == TransactionState.POSTED // deposit skips HELD
              || to == TransactionState.FAILED;
      case HELD -> to == TransactionState.POSTED || to == TransactionState.COMPENSATED;
      case POSTED -> to == TransactionState.SETTLED;
      case SETTLED, FAILED, COMPENSATED -> false;
    };
  }
}
