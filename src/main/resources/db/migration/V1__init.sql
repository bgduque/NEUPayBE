-- NeuPayment initial schema
-- All money columns are NUMERIC(19,4) so 4 decimals are preserved
-- (PHP centavos are 2 decimals; we keep 4 for safe intermediate math).

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name       VARCHAR(160) NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE,
    id_number       VARCHAR(32)  NOT NULL UNIQUE,
    program         VARCHAR(160),
    role            VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    password_hash   VARCHAR(120) NOT NULL,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT users_role_chk
        CHECK (role IN ('STUDENT','FACULTY','CASHIER','ADMIN')),
    CONSTRAINT users_status_chk
        CHECK (status IN ('VERIFIED','PENDING','SUSPENDED'))
);

CREATE INDEX idx_users_role ON users(role);

CREATE TABLE wallets (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID          NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    balance           NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    card_number       VARCHAR(32)   NOT NULL UNIQUE,
    status            VARCHAR(20)   NOT NULL,
    valid_until_year  INTEGER       NOT NULL,
    version           BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT wallets_status_chk
        CHECK (status IN ('ACTIVE','FROZEN','CLOSED'))
);

CREATE TABLE transactions (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id           UUID          NOT NULL REFERENCES wallets(id) ON DELETE CASCADE,
    counterparty_wallet UUID          REFERENCES wallets(id),
    amount              NUMERIC(19,4) NOT NULL,
    title               VARCHAR(160)  NOT NULL,
    category            VARCHAR(20)   NOT NULL,
    reference           VARCHAR(64)   NOT NULL UNIQUE,
    balance_after       NUMERIC(19,4) NOT NULL,
    occurred_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    initiated_by_user   UUID          REFERENCES users(id),
    metadata            JSONB,
    CONSTRAINT tx_category_chk
        CHECK (category IN ('DINING','TOP_UP','LIBRARY','REGISTRAR','TRANSFER','PAYMENT','REFUND','ADJUSTMENT'))
);

CREATE INDEX idx_tx_wallet_occurred ON transactions(wallet_id, occurred_at DESC);
CREATE INDEX idx_tx_initiated_by    ON transactions(initiated_by_user);

CREATE TABLE qr_tokens (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    nonce           VARCHAR(64)  NOT NULL UNIQUE,
    mode            VARCHAR(16)  NOT NULL,
    issued_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ  NOT NULL,
    consumed_at     TIMESTAMPTZ,
    consumed_by     UUID         REFERENCES users(id),
    CONSTRAINT qr_mode_chk CHECK (mode IN ('PAY_OUT','CASH_IN'))
);

CREATE INDEX idx_qr_user_active ON qr_tokens(user_id) WHERE consumed_at IS NULL;
CREATE INDEX idx_qr_expires     ON qr_tokens(expires_at);

CREATE TABLE biometric_credentials (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id         VARCHAR(120) NOT NULL,
    public_key_pem    TEXT         NOT NULL,
    key_algorithm     VARCHAR(40)  NOT NULL DEFAULT 'EC_SECP256R1',
    enrolled_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    revoked_at        TIMESTAMPTZ,
    UNIQUE(user_id, device_id)
);

CREATE TABLE biometric_challenges (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    credential_id UUID         REFERENCES biometric_credentials(id) ON DELETE CASCADE,
    challenge     VARCHAR(96)  NOT NULL UNIQUE,
    purpose       VARCHAR(40)  NOT NULL,
    issued_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ  NOT NULL,
    consumed_at   TIMESTAMPTZ
);

CREATE INDEX idx_biochall_user ON biometric_challenges(user_id) WHERE consumed_at IS NULL;

CREATE TABLE refresh_tokens (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(128) NOT NULL UNIQUE,
    device_id   VARCHAR(120),
    issued_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ
);

CREATE INDEX idx_refresh_user ON refresh_tokens(user_id) WHERE revoked_at IS NULL;

CREATE TABLE cash_in_locations (
    id        UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name      VARCHAR(160) NOT NULL,
    sublabel  VARCHAR(255),
    kind      VARCHAR(20)  NOT NULL,
    active    BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT cashin_kind_chk
        CHECK (kind IN ('MAIN_CASHIER','CANTEEN','KIOSK'))
);

CREATE TABLE audit_logs (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id  UUID         REFERENCES users(id),
    action         VARCHAR(80)  NOT NULL,
    entity_type    VARCHAR(60),
    entity_id      VARCHAR(80),
    ip_address     VARCHAR(64),
    user_agent     VARCHAR(255),
    details        JSONB,
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_actor    ON audit_logs(actor_user_id, occurred_at DESC);
CREATE INDEX idx_audit_entity   ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_occurred ON audit_logs(occurred_at DESC);

CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(120) PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    method          VARCHAR(10)  NOT NULL,
    path            VARCHAR(255) NOT NULL,
    response_body   TEXT,
    response_status INT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_idem_created ON idempotency_keys(created_at);
