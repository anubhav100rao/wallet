# 7. JWT Algorithm and Key Rotation Strategy

Date: 2026-05-10

## Status

Accepted

## Context

The identity context issues JWTs (JSON Web Tokens) for authentication. We need to decide how these tokens are signed, validated, and how cryptographic keys are managed. Symmetric signing (HS256) is simpler but requires sharing the secret with any service that needs to validate the token. Asymmetric signing (RS256/ES256) allows the identity service to sign tokens with a private key while other services validate them using a public key.

## Decision

We will use **Asymmetric Signing (RS256)** for JWTs.

- The `identity-service` holds the private key and signs tokens.
- The `identity-service` exposes a JSON Web Key Set (JWKS) endpoint at `/.well-known/jwks.json`.
- Other services (and the API Gateway) fetch the public keys from the JWKS endpoint to validate tokens without needing a shared secret.

### Key Management
- For local development, an RSA key pair is generated randomly in-memory at startup.
- For production, the key pair will be loaded from secure configuration/secrets.
- Key rotation is supported by maintaining a `kid` (Key ID) in the JWT header, which corresponds to an entry in the JWKS endpoint.

## Consequences

- **Positive:** Enhanced security. Resource servers do not possess the ability to mint new tokens.
- **Positive:** Fits perfectly with Spring Security's `oauth2ResourceServer().jwt()` auto-configuration which natively supports JWKS.
- **Negative:** Slightly more complex setup (key pair generation/loading) compared to a static symmetric secret.
