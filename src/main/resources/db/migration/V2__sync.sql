-- Sync support: dedupe key on transactions + per-user sync connections (Gmail/SMS).

ALTER TABLE transactions ADD COLUMN IF NOT EXISTS external_id text;

-- One transaction per (user, external source message) — prevents re-importing the
-- same Gmail message or SMS alert on every sync.
CREATE UNIQUE INDEX IF NOT EXISTS transactions_user_external_idx
  ON transactions (user_id, external_id)
  WHERE external_id IS NOT NULL;

DO $$ BEGIN
  CREATE TYPE sync_provider AS ENUM ('gmail', 'sms');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE TYPE sync_status AS ENUM ('pending', 'connected', 'error', 'revoked');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS sync_connections (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  provider sync_provider NOT NULL,
  email text,
  -- OAuth tokens for the provider (AES-GCM encrypted at rest for gmail).
  access_token_encrypted text,
  refresh_token_encrypted text,
  token_expires_at timestamptz,
  scope text,
  status sync_status NOT NULL DEFAULT 'pending',
  -- OAuth `state` used to match the browser callback to the pending connection.
  state text,
  last_synced_at timestamptz,
  last_synced_count integer NOT NULL DEFAULT 0,
  last_error text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS sync_connections_user_provider_idx ON sync_connections (user_id, provider);
CREATE INDEX IF NOT EXISTS sync_connections_state_idx ON sync_connections (state) WHERE state IS NOT NULL;

ALTER TABLE sync_connections ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS sync_connections_owner ON sync_connections;
CREATE POLICY sync_connections_owner ON sync_connections
  USING (user_id = app_uid())
  WITH CHECK (user_id = app_uid());