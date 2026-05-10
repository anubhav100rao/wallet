package com.wallet.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts", schema = "ledger")
public class Account {

  @Id private UUID id;

  @Column(name = "owner_user_id")
  private UUID ownerUserId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AccountType type;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  protected Account() {}

  public Account(UUID id, UUID ownerUserId, AccountType type, String currency) {
    this.id = id != null ? id : UUID.randomUUID();
    this.ownerUserId = ownerUserId;
    this.type = type;
    this.currency = currency;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOwnerUserId() {
    return ownerUserId;
  }

  public AccountType getType() {
    return type;
  }

  public String getCurrency() {
    return currency;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
