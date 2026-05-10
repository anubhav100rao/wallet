package com.wallet.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallets", schema = "wallet")
public class Wallet {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(nullable = false, length = 3, updatable = false)
  private String currency;

  @Column(name = "total_balance", nullable = false, precision = 19, scale = 4)
  private BigDecimal totalBalance = BigDecimal.ZERO;

  @Column(name = "available_balance", nullable = false, precision = 19, scale = 4)
  private BigDecimal availableBalance = BigDecimal.ZERO;

  @Version
  @Column(nullable = false)
  private Long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected Wallet() {}

  public Wallet(UUID userId, String currency) {
    this.userId = userId;
    this.currency = currency;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getCurrency() {
    return currency;
  }

  public BigDecimal getTotalBalance() {
    return totalBalance;
  }

  public BigDecimal getAvailableBalance() {
    return availableBalance;
  }

  public void debitAvailable(BigDecimal amount) {
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Amount must be positive");
    }
    if (availableBalance.compareTo(amount) < 0) {
      throw new InsufficientFundsException("Insufficient available balance");
    }
    this.availableBalance = this.availableBalance.subtract(amount);
    this.updatedAt = Instant.now();
  }

  public void creditAvailable(BigDecimal amount) {
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Amount must be positive");
    }
    this.availableBalance = this.availableBalance.add(amount);
    this.updatedAt = Instant.now();
  }

  public void debitTotal(BigDecimal amount) {
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Amount must be positive");
    }
    if (totalBalance.compareTo(amount) < 0) {
      throw new InsufficientFundsException("Insufficient total balance");
    }
    this.totalBalance = this.totalBalance.subtract(amount);
    this.updatedAt = Instant.now();
  }

  public void creditTotalAndAvailable(BigDecimal amount) {
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Amount must be positive");
    }
    this.totalBalance = this.totalBalance.add(amount);
    this.availableBalance = this.availableBalance.add(amount);
    this.updatedAt = Instant.now();
  }
}
