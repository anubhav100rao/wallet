# 8. Wallet Locking Strategy

Date: 2026-05-10

## Status

Accepted

## Context

When multiple transactions concurrently attempt to modify a wallet's `available_balance` or `total_balance`, we must prevent lost updates and race conditions. We need to choose a locking strategy.

- **Optimistic Locking:** Uses a `@Version` field. If concurrent modifications occur, the second one fails with an `OptimisticLockingFailureException` and must be retried by the application.
- **Pessimistic Locking:** Uses `SELECT ... FOR UPDATE` to acquire a database-level row lock. Concurrent modifications wait (up to a timeout) for the lock to be released, then proceed sequentially.

## Decision

We will use **Pessimistic Locking** (`SELECT ... FOR UPDATE`) as the primary strategy for the Wallet context.

While optimistic locking is faster under low contention, banking wallets often experience high contention (e.g., a merchant receiving many payments simultaneously, or a user initiating multiple rapid transfers). Under high contention, optimistic locking leads to excessive retries, starvation, and degraded throughput. Pessimistic locking forces sequential processing at the database level, ensuring correctness without application-level retry storms.

## Consequences

- **Positive:** High correctness and predictability under high contention.
- **Positive:** Simplifies application code (no complex retry mechanisms required for concurrent modifications).
- **Negative:** Database row locks can cause deadlocks if not acquired in a consistent order (e.g., when transferring between two wallets). However, since our saga design modifies one wallet at a time per step, deadlocks are inherently avoided.
