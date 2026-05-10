# 15. Ledger Entry Representation

Date: 2026-05-10

## Status

Accepted

## Context

The ledger context is the source of truth for all balances. A journal entry records a movement of funds. We must decide how to represent the accounting direction (debit/credit) and the amount.

- **Option A (Direction + Unsigned Amount):** Explicit `direction` column (`D` or `C`) and an unsigned `amount`. This strictly mimics double-entry accounting. The sum across a transaction requires a `CASE` statement: `SUM(CASE direction WHEN 'D' THEN -amount ELSE amount END) = 0`.
- **Option B (Signed Amount):** A single signed `amount` column where negative indicates debit and positive indicates credit. The sum across a transaction is simply `SUM(amount) = 0`.

## Decision

We choose **Option B (Signed Amount)** for the `journal_entries` table.

- Debits are negative values.
- Credits are positive values.
- A `CHECK (amount != 0)` constraint prevents zero-value entries.
- The fundamental double-entry invariant is enforced natively and simply via `SUM(amount) = 0`.

## Consequences

- **Positive:** Aggregations (like calculating balances) are trivial `SUM(amount)` operations without complex `CASE` statements.
- **Positive:** Easier indexing and querying for analytical purposes.
- **Negative:** We lose the explicit 'D' and 'C' accounting terminology in the database, though it can be exposed conceptually in the API if needed.
