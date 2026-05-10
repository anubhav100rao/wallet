-- V8__deposits_and_withdrawals.sql
-- Support deposit and withdrawal sagas:
--   * Deposits have no source wallet (cash arrives from outside).
--   * Withdrawals have no destination wallet (cash leaves to outside).
-- Both are reflected in the ledger via SYSTEM_CASH accounts (one per currency),
-- which are seeded here with deterministic UUIDs for cross-environment stability.

ALTER TABLE transaction.transactions
    ALTER COLUMN from_wallet_id DROP NOT NULL;

ALTER TABLE transaction.transactions
    ALTER COLUMN to_wallet_id DROP NOT NULL;

-- Seed one SYSTEM_CASH ledger account per supported currency.
-- IDs are deterministic so code can reference them without a runtime lookup race.
-- Encoding: last 12-hex segment is the currency code packed as ASCII hex (zero-padded).
--   INR -> 494e52, USD -> 555344, EUR -> 455552, GBP -> 474250, JPY -> 4a5059
INSERT INTO ledger.accounts (id, owner_user_id, type, currency, created_at) VALUES
    ('00000000-0000-0000-0000-000000494e52'::uuid, NULL, 'SYSTEM_CASH', 'INR', NOW()),
    ('00000000-0000-0000-0000-000000555344'::uuid, NULL, 'SYSTEM_CASH', 'USD', NOW()),
    ('00000000-0000-0000-0000-000000455552'::uuid, NULL, 'SYSTEM_CASH', 'EUR', NOW()),
    ('00000000-0000-0000-0000-000000474250'::uuid, NULL, 'SYSTEM_CASH', 'GBP', NOW()),
    ('00000000-0000-0000-0000-0000004a5059'::uuid, NULL, 'SYSTEM_CASH', 'JPY', NOW());
