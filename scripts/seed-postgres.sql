-- Banking Wallet — Database Seed Script
-- Creates one logical database per planned service.
-- Executed automatically by the postgres container on first startup
-- via /docker-entrypoint-initdb.d/

-- Identity service database
CREATE DATABASE identity_db OWNER wallet_admin;

-- Wallet service database
CREATE DATABASE wallet_db OWNER wallet_admin;

-- Ledger service database
CREATE DATABASE ledger_db OWNER wallet_admin;

-- Transaction service database
CREATE DATABASE transaction_db OWNER wallet_admin;

-- Notification service database
CREATE DATABASE notification_db OWNER wallet_admin;

-- Reconciliation service database
CREATE DATABASE reconciliation_db OWNER wallet_admin;

-- Audit service database
CREATE DATABASE audit_db OWNER wallet_admin;

-- Grant connect privileges
GRANT ALL PRIVILEGES ON DATABASE identity_db TO wallet_admin;
GRANT ALL PRIVILEGES ON DATABASE wallet_db TO wallet_admin;
GRANT ALL PRIVILEGES ON DATABASE ledger_db TO wallet_admin;
GRANT ALL PRIVILEGES ON DATABASE transaction_db TO wallet_admin;
GRANT ALL PRIVILEGES ON DATABASE notification_db TO wallet_admin;
GRANT ALL PRIVILEGES ON DATABASE reconciliation_db TO wallet_admin;
GRANT ALL PRIVILEGES ON DATABASE audit_db TO wallet_admin;

-- Log the result
DO $$
BEGIN
    RAISE NOTICE '✅ All service databases created successfully.';
END $$;
