package com.wallet.identity.api;

import java.util.UUID;

/**
 * Public interface for the Identity context.
 *
 * <p>Other bounded contexts (e.g., Wallet, Transaction) may use this interface to query identity
 * information without coupling to identity internals.
 */
public interface IdentityApi {

  /**
   * Checks if a user has completed KYC.
   *
   * @param userId the user's UUID
   * @return true if KYC is verified, false otherwise
   */
  boolean isKycVerified(UUID userId);
}
