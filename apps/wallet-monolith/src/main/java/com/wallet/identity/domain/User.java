package com.wallet.identity.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users", schema = "identity")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true, columnDefinition = "citext")
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "kyc_status", nullable = false)
  private KycStatus kycStatus = KycStatus.NONE;

  @Column(name = "token_version", nullable = false)
  private int tokenVersion = 1;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "user_roles",
      schema = "identity",
      joinColumns = @JoinColumn(name = "user_id"))
  @Column(name = "role")
  private Set<String> roles = new HashSet<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  protected User() {
    // JPA
  }

  public User(String email, String passwordHash) {
    this.email = email;
    this.passwordHash = passwordHash;
    this.roles.add("ROLE_USER");
  }

  public void revokeTokens() {
    this.tokenVersion++;
  }

  public void verifyKyc() {
    this.kycStatus = KycStatus.VERIFIED;
  }

  public void markKycPending() {
    this.kycStatus = KycStatus.PENDING;
  }

  // Getters
  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public KycStatus getKycStatus() {
    return kycStatus;
  }

  public int getTokenVersion() {
    return tokenVersion;
  }

  public Set<String> getRoles() {
    return roles;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
