# 13. Access-Token Revocation Strategy

Date: 2026-05-10

## Status

Accepted

## Context

JWTs are stateless, meaning once issued, they cannot be revoked by deleting a session in a database. If a user's account is compromised, their password changed, or their KYC revoked, we must invalidate their existing access tokens. 

Options:
1. **Long TTL (e.g., 1 hour) with Redis Blacklist:** Fast, immediate revocation but requires every resource server to check Redis on every request, defeating the stateless benefit of JWTs.
2. **Short TTL (e.g., 5 mins) with no blacklist:** Very simple. Revocation relies on the access token expiring naturally. Refresh tokens are used to get new access tokens and can be revoked centrally. A compromised token is valid for at most 5 minutes.
3. **`tokenVersion` claim:** Embed a `tokenVersion` counter in the JWT and user record. When a user is revoked/password changed, increment their database `tokenVersion`. Resource servers check the JWT version against a cached version of the user.

## Decision

We will use a combination of **Option 2 and Option 3**:
- **Short TTL:** Access tokens are valid for 5 minutes.
- **Refresh Token Rotation:** Refresh tokens are long-lived but rotated on use. Reusing a revoked refresh token revokes the entire token family.
- **`tokenVersion` Claim:** The JWT will carry a `tokenVersion`. While we won't strictly enforce version checking on *every* request immediately, this sets the foundation.

For Phase 1 and 2, the primary revocation mechanism is the **Short TTL**. If a user's password is changed or account suspended, all of their refresh tokens are revoked. Within 5 minutes, their access token expires and they cannot get a new one.

## Consequences

- **Positive:** System remains mostly stateless. Resource servers validate JWT signatures locally without an external lookup.
- **Positive:** Refresh token rotation guards against stolen refresh tokens.
- **Negative:** There is a vulnerability window of up to 5 minutes where a revoked user can still make requests. This is widely considered an acceptable trade-off in modern architectures.
