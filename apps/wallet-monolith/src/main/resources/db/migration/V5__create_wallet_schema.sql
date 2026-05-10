-- V5__create_wallet_schema.sql
-- Schema and tables for the wallet bounded context.

CREATE SCHEMA IF NOT EXISTS wallet;

CREATE TABLE wallet.wallets (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    currency VARCHAR(3) NOT NULL,
    total_balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    available_balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, currency)
);

CREATE TABLE wallet.wallet_holds (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    wallet_id UUID NOT NULL REFERENCES wallet.wallets(id),
    transaction_id UUID NOT NULL,
    amount NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    state VARCHAR(20) NOT NULL CHECK (state IN ('ACTIVE', 'RELEASED', 'CAPTURED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(wallet_id, transaction_id)
);

CREATE TABLE wallet.wallet_credits (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    wallet_id UUID NOT NULL REFERENCES wallet.wallets(id),
    transaction_id UUID NOT NULL,
    amount NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(wallet_id, transaction_id)
);

CREATE INDEX idx_wallet_holds_state_expires ON wallet.wallet_holds(state, expires_at);
