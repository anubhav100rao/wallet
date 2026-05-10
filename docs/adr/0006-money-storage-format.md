# 6. Money Storage Format

Date: 2026-05-10

## Status

Accepted

## Context

Money values appear throughout the system: wallet balances, journal entries, hold amounts, transaction amounts. We must choose a storage format that:
1. Never loses precision (no floating point).
2. Carries its currency explicitly (no implicit "the system is INR").
3. Maps cleanly between Postgres, Java, and JSON.

Options considered:
- **A: `NUMERIC(19,4)` + `CHAR(3)` (two columns)** — stores amount as a decimal with 4 fractional digits and currency as an ISO 4217 code. Java maps to `BigDecimal` + `Currency` enum.
- **B: `BIGINT` minor units + `CHAR(3)` (two columns)** — stores 100.00 INR as `1000000` (paise × 100 for extra precision). Simpler math, but requires scale management.

## Decision

**Option A: `NUMERIC(19,4)` for amounts, `CHAR(3)` for currency. Always two columns, never one.**

In Java:
- `Money` is an `@Embeddable` value object with `BigDecimal amount` (scale 4, `RoundingMode.HALF_EVEN`) and `Currency currency` (enum stored as `CHAR(3)`).
- Constructed via static factories: `Money.of(BigDecimal, Currency)`, `Money.zero(Currency)`, `Money.minor(long, Currency)`.
- `BigDecimal` equality uses `compareTo`, not `equals` (because `new BigDecimal("100.00").equals(new BigDecimal("100.0000"))` is `false`).

In JSON:
```json
{ "amount": "100.0000", "currency": "INR" }
```
Amount is serialized as a **string**, not a number — JSON numbers lose precision in some clients (JavaScript's `Number.MAX_SAFE_INTEGER` is 2^53).

In SQL:
```sql
amount NUMERIC(19,4) NOT NULL,
currency CHAR(3) NOT NULL
```

## Consequences

- **Positive:** `NUMERIC(19,4)` supports up to 15 integer digits and 4 decimal places — sufficient for any realistic wallet amount. 4 decimal places support currencies with sub-cent units.
- **Positive:** Two-column storage makes every money column self-describing. No implicit currency anywhere.
- **Positive:** JSON string encoding avoids client-side precision loss.
- **Negative:** `BigDecimal` arithmetic is verbose in Java (no operator overloading). Mitigated by the `Money` value object encapsulating all operations.
- **Negative:** 4 decimal places may be excessive for currencies like JPY (0 decimals). Accepted: the trailing zeros are harmless and the uniform scale simplifies the codebase.
