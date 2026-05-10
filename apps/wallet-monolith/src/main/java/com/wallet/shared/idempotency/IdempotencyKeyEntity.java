package com.wallet.shared.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Entity representing an executed (or currently executing) idempotent request. */
@Entity
@Table(name = "idempotency_keys", schema = "shared")
public class IdempotencyKeyEntity {

  @Id
  @Column(name = "key_id")
  private String keyId;

  @Column(name = "request_hash", nullable = false, length = 64)
  private String requestHash;

  @Column(name = "response_status")
  private Integer responseStatus;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "response_body", columnDefinition = "jsonb")
  private String responseBody;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected IdempotencyKeyEntity() {
    // JPA
  }

  public IdempotencyKeyEntity(String keyId, String requestHash) {
    this.keyId = keyId;
    this.requestHash = requestHash;
    this.createdAt = Instant.now();
  }

  public void complete(int status, String body) {
    this.responseStatus = status;
    this.responseBody = body;
  }

  public boolean isCompleted() {
    return responseStatus != null;
  }

  // Getters
  public String getKeyId() {
    return keyId;
  }

  public String getRequestHash() {
    return requestHash;
  }

  public Integer getResponseStatus() {
    return responseStatus;
  }

  public String getResponseBody() {
    return responseBody;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
