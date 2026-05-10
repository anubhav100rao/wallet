# 11. Idempotency Key TTL and Reuse Semantics

Date: 2026-05-10

## Status

Accepted

## Context

Financial platforms require strict idempotency. Network failures often leave clients unsure if a request succeeded or failed, prompting them to retry. If a transfer endpoint is not idempotent, a retry could result in a double charge. 

We need an idempotency library to ensure that executing the same mutating request (with the same `Idempotency-Key` header) multiple times results in exactly one state change, while returning the original response to subsequent calls.

## Decision

We implement an `@Idempotent` mechanism (via Interceptor or Filter) with the following semantics:

1. **Storage:** We store keys in an `idempotency_keys` Postgres table: `(key PK, request_hash, response_status, response_body, created_at)`.
2. **Matching Hash:** When a request is received, we compute the SHA-256 hash of the request body. If the `Idempotency-Key` already exists in the table:
   - If the `request_hash` **matches**, we short-circuit the request and return the stored `response_status` and `response_body` (successful replay).
   - If the `request_hash` **does not match**, we return `409 Conflict` indicating the key was reused with a different payload.
3. **Concurrency:** To prevent race conditions on concurrent identical requests, the idempotency mechanism inserts the key into the database in a separate, independent transaction *before* business logic executes (or relies on primary key constraints). 
4. **TTL (Time To Live):** Keys will be purged after 24 hours via a scheduled cron job (`IdempotencyKeyPurgeJob`). This is long enough to handle immediate network retries but prevents unbounded table growth. 

## Consequences

- **Positive:** Safely handles client retries without duplicating side effects.
- **Positive:** Rejecting mismatched hashes prevents bugs where clients reuse UUIDs for logically different requests.
- **Negative:** Adds database latency (1 insert, 1 update) to every idempotent request.
- **Negative:** 24-hour TTL means a retry sent on day 2 will be treated as a new request and potentially double-charge. Clients must generate new keys if they legitimately want to submit the same payload the next day.
