CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$ BEGIN
  CREATE TYPE plan AS ENUM ('Free', 'Plus');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE TYPE account_type AS ENUM ('Savings', 'Salary', 'FD', 'Wallet');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE TYPE account_status AS ENUM ('connected', 'attention');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE TYPE category_kind AS ENUM ('income', 'expense');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE TYPE txn_source AS ENUM ('sms', 'email', 'statement', 'investment', 'manual');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE TYPE holding_klass AS ENUM ('Equity', 'Debt', 'Gold', 'Cash & FD');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE TYPE investment_type AS ENUM ('SIP', 'BUY', 'SELL', 'CREDIT');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE TYPE recurring_cycle AS ENUM ('Weekly', 'Monthly', 'Quarterly', 'Yearly');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS profiles (
  id uuid PRIMARY KEY,
  name text NOT NULL,
  email text NOT NULL,
  plan plan NOT NULL DEFAULT 'Free',
  member_since timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS accounts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  bank text NOT NULL,
  name text NOT NULL,
  mask varchar(8) NOT NULL,
  type account_type NOT NULL,
  balance integer NOT NULL DEFAULT 0,
  change_pct numeric(8, 2) NOT NULL DEFAULT 0,
  color text NOT NULL DEFAULT '#4F46E5',
  inflow integer NOT NULL DEFAULT 0,
  outflow integer NOT NULL DEFAULT 0,
  status account_status NOT NULL DEFAULT 'connected',
  last_synced_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS accounts_user_id_idx ON accounts (user_id);

CREATE TABLE IF NOT EXISTS categories (
  key text PRIMARY KEY,
  label text NOT NULL,
  kind category_kind NOT NULL
);

INSERT INTO categories (key, label, kind) VALUES
  ('salary', 'Salary', 'income'),
  ('freelance', 'Freelance', 'income'),
  ('dividend', 'Dividend', 'income'),
  ('interest', 'Interest', 'income'),
  ('rent', 'Rent', 'expense'),
  ('groceries', 'Groceries', 'expense'),
  ('dining', 'Dining', 'expense'),
  ('transport', 'Transport', 'expense'),
  ('utilities', 'Utilities', 'expense'),
  ('shopping', 'Shopping', 'expense'),
  ('health', 'Health', 'expense'),
  ('entertainment', 'Entertainment', 'expense'),
  ('emi', 'EMI', 'expense'),
  ('investment', 'Investment', 'expense'),
  ('other', 'Other', 'expense')
ON CONFLICT (key) DO NOTHING;

CREATE TABLE IF NOT EXISTS transactions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  account_id uuid NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
  category_key text NOT NULL REFERENCES categories (key),
  title text NOT NULL,
  note text,
  amount integer NOT NULL,
  date timestamptz NOT NULL,
  source txn_source NOT NULL DEFAULT 'manual',
  confidence numeric(4, 3),
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT transactions_confidence_range CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1))
);
CREATE INDEX IF NOT EXISTS transactions_user_date_idx ON transactions (user_id, date DESC);
CREATE INDEX IF NOT EXISTS transactions_user_account_idx ON transactions (user_id, account_id);
CREATE INDEX IF NOT EXISTS transactions_user_category_idx ON transactions (user_id, category_key);

CREATE TABLE IF NOT EXISTS holdings (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  name text NOT NULL,
  issuer text NOT NULL,
  klass holding_klass NOT NULL,
  value integer NOT NULL DEFAULT 0,
  invested integer NOT NULL DEFAULT 0,
  xirr numeric(8, 2) NOT NULL DEFAULT 0,
  color text NOT NULL DEFAULT '#4F46E5'
);
CREATE INDEX IF NOT EXISTS holdings_user_id_idx ON holdings (user_id);

CREATE TABLE IF NOT EXISTS investment_history (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  date timestamptz NOT NULL,
  title text NOT NULL,
  type investment_type NOT NULL,
  amount integer NOT NULL
);
CREATE INDEX IF NOT EXISTS investment_history_user_date_idx ON investment_history (user_id, date DESC);

CREATE TABLE IF NOT EXISTS recurring (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  name text NOT NULL,
  amount integer NOT NULL,
  cycle recurring_cycle NOT NULL DEFAULT 'Monthly',
  next_date date NOT NULL,
  change text,
  icon text
);
CREATE INDEX IF NOT EXISTS recurring_user_id_idx ON recurring (user_id);

CREATE TABLE IF NOT EXISTS goals (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  name text NOT NULL,
  target integer NOT NULL,
  saved integer NOT NULL DEFAULT 0,
  due_date date NOT NULL,
  color text NOT NULL DEFAULT '#4F46E5'
);
CREATE INDEX IF NOT EXISTS goals_user_id_idx ON goals (user_id);

CREATE TABLE IF NOT EXISTS sessions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  refresh_token_hash text NOT NULL,
  user_agent text,
  ip text,
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  revoked_at timestamptz
);
CREATE INDEX IF NOT EXISTS sessions_user_id_idx ON sessions (user_id);
CREATE UNIQUE INDEX IF NOT EXISTS sessions_refresh_hash_idx ON sessions (refresh_token_hash);

CREATE TABLE IF NOT EXISTS monthly_summary (
  user_id uuid NOT NULL,
  month date NOT NULL,
  income integer NOT NULL DEFAULT 0,
  spending integer NOT NULL DEFAULT 0,
  invested integer NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, month)
);

CREATE OR REPLACE VIEW monthly_summary_view AS
SELECT
  t.user_id,
  date_trunc('month', t.date)::date AS month,
  COALESCE(SUM(CASE WHEN t.amount > 0 AND c.kind = 'income' THEN t.amount ELSE 0 END), 0)::integer AS income,
  COALESCE(SUM(CASE WHEN t.amount < 0 AND c.kind = 'expense' AND t.category_key <> 'investment' THEN ABS(t.amount) ELSE 0 END), 0)::integer AS spending,
  COALESCE(SUM(CASE WHEN t.category_key = 'investment' THEN ABS(t.amount) ELSE 0 END), 0)::integer AS invested
FROM transactions t
JOIN categories c ON c.key = t.category_key
GROUP BY t.user_id, date_trunc('month', t.date)::date;

CREATE MATERIALIZED VIEW IF NOT EXISTS monthly_summary_mv AS
SELECT * FROM monthly_summary_view
WITH NO DATA;

CREATE UNIQUE INDEX IF NOT EXISTS monthly_summary_mv_pk ON monthly_summary_mv (user_id, month);

CREATE OR REPLACE FUNCTION refresh_monthly_summary()
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
  REFRESH MATERIALIZED VIEW CONCURRENTLY monthly_summary_mv;
EXCEPTION WHEN OTHERS THEN
  REFRESH MATERIALIZED VIEW monthly_summary_mv;
END;
$$;

CREATE OR REPLACE FUNCTION set_user_id_from_jwt()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.user_id IS NULL THEN
    NEW.user_id := NULLIF(current_setting('request.jwt.claim.sub', true), '')::uuid;
  END IF;
  RETURN NEW;
END;
$$;

ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE holdings ENABLE ROW LEVEL SECURITY;
ALTER TABLE investment_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE recurring ENABLE ROW LEVEL SECURITY;
ALTER TABLE goals ENABLE ROW LEVEL SECURITY;
ALTER TABLE sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE monthly_summary ENABLE ROW LEVEL SECURITY;

CREATE OR REPLACE FUNCTION app_uid()
RETURNS uuid
LANGUAGE sql
STABLE
AS $$
  SELECT NULLIF(
    COALESCE(
      current_setting('request.jwt.claim.sub', true),
      current_setting('app.user_id', true)
    ),
    ''
  )::uuid;
$$;

DROP POLICY IF EXISTS profiles_owner ON profiles;
CREATE POLICY profiles_owner ON profiles
  USING (id = app_uid())
  WITH CHECK (id = app_uid());

DROP POLICY IF EXISTS accounts_owner ON accounts;
CREATE POLICY accounts_owner ON accounts
  USING (user_id = app_uid())
  WITH CHECK (user_id = app_uid());

DROP POLICY IF EXISTS transactions_owner ON transactions;
CREATE POLICY transactions_owner ON transactions
  USING (user_id = app_uid())
  WITH CHECK (user_id = app_uid());

DROP POLICY IF EXISTS holdings_owner ON holdings;
CREATE POLICY holdings_owner ON holdings
  USING (user_id = app_uid())
  WITH CHECK (user_id = app_uid());

DROP POLICY IF EXISTS investment_history_owner ON investment_history;
CREATE POLICY investment_history_owner ON investment_history
  USING (user_id = app_uid())
  WITH CHECK (user_id = app_uid());

DROP POLICY IF EXISTS recurring_owner ON recurring;
CREATE POLICY recurring_owner ON recurring
  USING (user_id = app_uid())
  WITH CHECK (user_id = app_uid());

DROP POLICY IF EXISTS goals_owner ON goals;
CREATE POLICY goals_owner ON goals
  USING (user_id = app_uid())
  WITH CHECK (user_id = app_uid());

DROP POLICY IF EXISTS sessions_owner ON sessions;
CREATE POLICY sessions_owner ON sessions
  USING (user_id = app_uid())
  WITH CHECK (user_id = app_uid());

DROP POLICY IF EXISTS monthly_summary_owner ON monthly_summary;
CREATE POLICY monthly_summary_owner ON monthly_summary
  USING (user_id = app_uid())
  WITH CHECK (user_id = app_uid());

GRANT SELECT ON categories TO PUBLIC;
