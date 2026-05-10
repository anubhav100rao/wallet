-- Local development seed data.
--
-- Loaded only when the Spring "local" profile is active and wallet.seed.enabled=true.
-- Demo users all use password: password123

-- Identity -------------------------------------------------------------------

INSERT INTO identity.users (id, email, password_hash, kyc_status, token_version, created_at)
VALUES
  ('20000000-0000-0000-0000-000000000001', 'alice@wallet.local', '${ALICE_PASSWORD_HASH}', 'VERIFIED', 1, NOW()),
  ('20000000-0000-0000-0000-000000000002', 'bob@wallet.local', '${BOB_PASSWORD_HASH}', 'VERIFIED', 1, NOW()),
  ('20000000-0000-0000-0000-000000000003', 'charlie@wallet.local', '${CHARLIE_PASSWORD_HASH}', 'PENDING', 1, NOW())
ON CONFLICT (id) DO UPDATE SET
  email = EXCLUDED.email,
  password_hash = EXCLUDED.password_hash,
  kyc_status = EXCLUDED.kyc_status,
  token_version = EXCLUDED.token_version;

INSERT INTO identity.user_roles (user_id, role)
VALUES
  ('20000000-0000-0000-0000-000000000001', 'ROLE_USER'),
  ('20000000-0000-0000-0000-000000000002', 'ROLE_USER'),
  ('20000000-0000-0000-0000-000000000003', 'ROLE_USER')
ON CONFLICT (user_id, role) DO NOTHING;

-- Wallets --------------------------------------------------------------------

INSERT INTO wallet.wallets (
  id, user_id, currency, total_balance, available_balance, version, created_at, updated_at
) VALUES
  ('10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 'USD', 1000.0000, 925.0000, 0, NOW(), NOW()),
  ('10000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000002', 'USD', 250.0000, 250.0000, 0, NOW(), NOW()),
  ('10000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000003', 'INR', 5000.0000, 5000.0000, 0, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET
  total_balance = EXCLUDED.total_balance,
  available_balance = EXCLUDED.available_balance,
  version = EXCLUDED.version,
  updated_at = NOW();

INSERT INTO ledger.accounts (id, owner_user_id, type, currency, created_at)
VALUES
  ('10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 'USER_CASH', 'USD', NOW()),
  ('10000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000002', 'USER_CASH', 'USD', NOW()),
  ('10000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000003', 'USER_CASH', 'INR', NOW())
ON CONFLICT (id) DO UPDATE SET
  owner_user_id = EXCLUDED.owner_user_id,
  type = EXCLUDED.type,
  currency = EXCLUDED.currency;

-- Transaction examples --------------------------------------------------------

INSERT INTO transaction.transactions (
  id, type, state, idempotency_key, from_wallet_id, to_wallet_id, amount, currency, created_at, updated_at
) VALUES
  (
    '30000000-0000-0000-0000-000000000001',
    'P2P_TRANSFER',
    'SETTLED',
    'seed-transfer-settled-usd',
    '10000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000002',
    25.0000,
    'USD',
    NOW(),
    NOW()
  ),
  (
    '30000000-0000-0000-0000-000000000002',
    'DEPOSIT',
    'SETTLED',
    'seed-deposit-settled-usd',
    NULL,
    '10000000-0000-0000-0000-000000000002',
    100.0000,
    'USD',
    NOW(),
    NOW()
  ),
  (
    '30000000-0000-0000-0000-000000000003',
    'P2P_TRANSFER',
    'HELD',
    'seed-transfer-held-usd',
    '10000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000002',
    75.0000,
    'USD',
    NOW(),
    NOW()
  )
ON CONFLICT (id) DO UPDATE SET
  state = EXCLUDED.state,
  updated_at = NOW();

-- Wallet hold/credit examples -------------------------------------------------

INSERT INTO wallet.wallet_holds (
  id, wallet_id, transaction_id, amount, state, created_at, expires_at
) VALUES
  (
    '40000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000001',
    '30000000-0000-0000-0000-000000000003',
    75.0000,
    'ACTIVE',
    NOW(),
    NOW() + INTERVAL '15 minutes'
  )
ON CONFLICT (id) DO UPDATE SET
  amount = EXCLUDED.amount,
  state = EXCLUDED.state,
  expires_at = EXCLUDED.expires_at;

INSERT INTO wallet.wallet_credits (
  id, wallet_id, transaction_id, amount, created_at
) VALUES
  (
    '41000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000002',
    '30000000-0000-0000-0000-000000000001',
    25.0000,
    NOW()
  ),
  (
    '41000000-0000-0000-0000-000000000002',
    '10000000-0000-0000-0000-000000000002',
    '30000000-0000-0000-0000-000000000002',
    100.0000,
    NOW()
  )
ON CONFLICT (id) DO NOTHING;

-- Ledger examples -------------------------------------------------------------

INSERT INTO ledger.journal_entries (
  id, transaction_id, account_id, amount, currency, posted_at, metadata
) VALUES
  (
    '50000000-0000-0000-0000-000000000001',
    '30000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000001',
    -25.0000,
    'USD',
    NOW(),
    '{"seed":"local-dev","type":"P2P_TRANSFER"}'::jsonb
  ),
  (
    '50000000-0000-0000-0000-000000000002',
    '30000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000002',
    25.0000,
    'USD',
    NOW(),
    '{"seed":"local-dev","type":"P2P_TRANSFER"}'::jsonb
  )
ON CONFLICT (id) DO NOTHING;

INSERT INTO ledger.journal_entries (
  id, transaction_id, account_id, amount, currency, posted_at, metadata
) VALUES
  (
    '50000000-0000-0000-0000-000000000003',
    '30000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000555344',
    -100.0000,
    'USD',
    NOW(),
    '{"seed":"local-dev","type":"DEPOSIT"}'::jsonb
  ),
  (
    '50000000-0000-0000-0000-000000000004',
    '30000000-0000-0000-0000-000000000002',
    '10000000-0000-0000-0000-000000000002',
    100.0000,
    'USD',
    NOW(),
    '{"seed":"local-dev","type":"DEPOSIT"}'::jsonb
  )
ON CONFLICT (id) DO NOTHING;

-- Cached idempotency responses ------------------------------------------------

INSERT INTO shared.idempotency_keys (
  key_id, request_hash, response_status, response_body, created_at
) VALUES
  (
    'seed-transfer-settled-usd',
    repeat('0', 64),
    200,
    '{"transactionId":"30000000-0000-0000-0000-000000000001","status":"SETTLED"}'::jsonb,
    NOW()
  ),
  (
    'seed-deposit-settled-usd',
    repeat('1', 64),
    200,
    '{"transactionId":"30000000-0000-0000-0000-000000000002","status":"SETTLED"}'::jsonb,
    NOW()
  )
ON CONFLICT (key_id) DO UPDATE SET
  response_status = EXCLUDED.response_status,
  response_body = EXCLUDED.response_body;

-- Outbox examples -------------------------------------------------------------

INSERT INTO shared.outbox_events (
  id, aggregate_type, aggregate_id, event_type, payload, headers, created_at, published_at, attempts
) VALUES
  (
    '60000000-0000-0000-0000-000000000001',
    'transaction',
    '30000000-0000-0000-0000-000000000001',
    'transfer.completed',
    '{"transactionId":"30000000-0000-0000-0000-000000000001","seed":true}'::jsonb,
    '{"source":"local-seed"}'::jsonb,
    NOW(),
    NOW(),
    0
  ),
  (
    '60000000-0000-0000-0000-000000000002',
    'user',
    '20000000-0000-0000-0000-000000000003',
    'user.kyc_pending',
    '{"userId":"20000000-0000-0000-0000-000000000003","seed":true}'::jsonb,
    '{"source":"local-seed"}'::jsonb,
    NOW(),
    NULL,
    0
  )
ON CONFLICT (id) DO UPDATE SET
  payload = EXCLUDED.payload,
  headers = EXCLUDED.headers;
