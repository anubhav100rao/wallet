package com.wallet.identity.api;

import java.time.Instant;
import java.util.UUID;

/** Event published when a new user registers. */
public record UserRegistered(UUID userId, String email, Instant registeredAt) {}
