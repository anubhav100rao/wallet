package com.wallet.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallet_holds", schema = "wallet")
public class WalletHold {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "wallet_id", nullable = false, updatable = false)
  private Wallet wallet;

  @Column(name = "transaction_id", nullable = false, updatable = false)
  private UUID transactionId;

  @Column(nullable = false, updatable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private HoldState state;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  protected WalletHold() {}

  public WalletHold(Wallet wallet, UUID transactionId, BigDecimal amount, Instant expiresAt) {
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Hold amount must be positive");
    }
    this.wallet = wallet;
    this.transactionId = transactionId;
    this.amount = amount;
    this.state = HoldState.ACTIVE;
    this.expiresAt = expiresAt;
  }

  public UUID getId() {
    return id;
  }

  public Wallet getWallet() {
    return wallet;
  }

  public UUID getTransactionId() {
    return transactionId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public HoldState getState() {
    return state;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void markReleased() {
    this.state = HoldState.RELEASED;
  }

  public void markCaptured() {
    this.state = HoldState.CAPTURED;
  }
}
