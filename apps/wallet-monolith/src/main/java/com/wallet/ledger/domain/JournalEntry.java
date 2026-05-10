package com.wallet.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "journal_entries", schema = "ledger")
public class JournalEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "transaction_id", nullable = false, updatable = false)
  private UUID transactionId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "account_id", nullable = false, updatable = false)
  private Account account;

  @Column(nullable = false, updatable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  @Column(nullable = false, length = 3, updatable = false)
  private String currency;

  @Column(name = "posted_at", nullable = false, updatable = false)
  private Instant postedAt = Instant.now();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb", updatable = false)
  private String metadata;

  protected JournalEntry() {}

  public JournalEntry(
      UUID transactionId, Account account, BigDecimal amount, String currency, String metadata) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
      throw new IllegalArgumentException("Journal entry amount cannot be zero");
    }
    if (!account.getCurrency().equals(currency)) {
      throw new IllegalArgumentException("Currency mismatch between entry and account");
    }
    this.transactionId = transactionId;
    this.account = account;
    this.amount = amount;
    this.currency = currency;
    this.metadata = metadata;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTransactionId() {
    return transactionId;
  }

  public Account getAccount() {
    return account;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public Instant getPostedAt() {
    return postedAt;
  }

  public String getMetadata() {
    return metadata;
  }
}
