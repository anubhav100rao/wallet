package com.wallet.wallet.service;

/**
 * Wallet row-locking strategy. Selected via the {@code wallet.locking.strategy} property at
 * startup; flip to compare correctness and throughput under contention.
 *
 * <ul>
 *   <li>{@link #PESSIMISTIC} — {@code SELECT … FOR UPDATE}: blocks contending writers at the row
 *       level. Single attempt, never retries (the lock guarantees consistency).
 *   <li>{@link #OPTIMISTIC} — plain read + {@code @Version} check on save: lock-free read, version
 *       mismatch throws {@code OptimisticLockingFailureException}. Wraps each operation in a small
 *       retry loop with backoff so transient contention doesn't surface to the caller.
 * </ul>
 *
 * <p>Pessimistic wins under high contention on a hot wallet (one Postgres tx queues, the rest wait
 * cheaply on the row lock). Optimistic wins when contention is rare (no lock acquisition cost) but
 * collapses under thrash. The benchmark in {@code WalletConcurrencyTest} demonstrates both.
 */
public enum LockingStrategy {
  PESSIMISTIC,
  OPTIMISTIC
}
