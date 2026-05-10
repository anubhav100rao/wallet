-- V4__create_ledger_schema.sql
-- Schema and tables for the ledger bounded context.

CREATE SCHEMA IF NOT EXISTS ledger;

CREATE TABLE ledger.accounts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_user_id UUID, -- Can be null for system/suspense accounts
    type VARCHAR(50) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE ledger.journal_entries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transaction_id UUID NOT NULL,
    account_id UUID NOT NULL REFERENCES ledger.accounts(id),
    amount NUMERIC(19,4) NOT NULL CHECK (amount != 0),
    currency VARCHAR(3) NOT NULL,
    posted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    metadata JSONB
);

-- Index for querying balances and transaction entries
CREATE INDEX idx_journal_entries_account_id ON ledger.journal_entries(account_id);
CREATE INDEX idx_journal_entries_transaction_id ON ledger.journal_entries(transaction_id);

-- Enforce zero-sum invariant per transaction. 
-- In PostgreSQL, deferred constraints allow us to check the invariant at the end of the transaction.
CREATE OR REPLACE FUNCTION ledger.check_zero_sum() RETURNS TRIGGER AS $$
DECLARE
    sum_amount NUMERIC;
BEGIN
    SELECT SUM(amount) INTO sum_amount FROM ledger.journal_entries WHERE transaction_id = NEW.transaction_id;
    IF sum_amount != 0 THEN
        RAISE EXCEPTION 'Unbalanced journal entry for transaction_id %: sum is %', NEW.transaction_id, sum_amount;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER ensure_zero_sum
    AFTER INSERT ON ledger.journal_entries
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION ledger.check_zero_sum();

-- Security: Create a dedicated role that only has INSERT/SELECT privileges on journal_entries.
-- For local dev with a single db user (app_user), we will just simulate this by granting roles,
-- but the real enforcement requires the app's ledger DataSource to connect as ledger_writer.
-- For Phase 1 we just document the intent in this SQL.
-- CREATE ROLE ledger_writer;
-- GRANT INSERT, SELECT ON ledger.journal_entries TO ledger_writer;
-- GRANT SELECT ON ledger.accounts TO ledger_writer;
