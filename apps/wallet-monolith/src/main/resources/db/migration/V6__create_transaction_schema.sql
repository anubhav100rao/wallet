-- V6__create_transaction_schema.sql
-- Schema and tables for the transaction (saga) bounded context.

CREATE SCHEMA IF NOT EXISTS transaction;

CREATE TABLE transaction.transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    type VARCHAR(50) NOT NULL,
    state VARCHAR(50) NOT NULL,
    idempotency_key VARCHAR(255) UNIQUE,
    from_wallet_id UUID NOT NULL,
    to_wallet_id UUID NOT NULL,
    amount NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transactions_from_wallet ON transaction.transactions(from_wallet_id);
CREATE INDEX idx_transactions_to_wallet ON transaction.transactions(to_wallet_id);
