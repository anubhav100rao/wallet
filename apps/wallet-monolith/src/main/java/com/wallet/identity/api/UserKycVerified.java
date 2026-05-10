package com.wallet.identity.api;

import java.time.Instant;
import java.util.UUID;

/** Event published when a user's KYC is verified. */
public record UserKycVerified(UUID userId, Instant verifiedAt) {}
