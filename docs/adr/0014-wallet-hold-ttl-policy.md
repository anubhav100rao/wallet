# 14. Wallet Hold TTL and Expiry Policy

Date: 2026-05-10

## Status

Accepted

## Context

When a transfer is initiated, the funds in the source wallet are "held" (deducted from `available_balance` but still in `total_balance`). This ensures the funds are reserved while the ledger is updated asynchronously. 

If the transaction saga stalls, crashes, or takes too long, we must ensure these holds do not persist indefinitely, which would lock the user's funds permanently.

## Decision

We implement a **Hold Expiry Sweeper** with a TTL policy:

- **TTL:** Holds are valid for **15 minutes**. When a hold is created, `expires_at` is set to `now() + 15m`.
- **Sweeper:** A `@Scheduled` background job runs every 30 seconds to find any `ACTIVE` hold where `expires_at < now()`.
- **Action:** The sweeper calls `WalletService.releaseHold(holdId)`, which returns the funds to `available_balance` and marks the hold as `RELEASED`. It also emits a `WalletHoldExpired` event.

## Consequences

- **Positive:** Guarantees that users regain access to their funds if a system failure interrupts a transaction.
- **Positive:** Self-healing architecture without manual intervention.
- **Negative:** If a legitimate ledger update takes longer than 15 minutes (extremely unlikely in an automated system), the hold could expire before capture, leading to a negative `available_balance` when the capture finally occurs. We will allow this to protect the ledger's integrity, but monitor for `expires_at` breaches.
