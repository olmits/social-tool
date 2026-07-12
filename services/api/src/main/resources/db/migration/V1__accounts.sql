CREATE TABLE accounts (
    id             UUID PRIMARY KEY,
    platform       VARCHAR(20)  NOT NULL,
    handle         VARCHAR(255) NOT NULL,
    credential_ref VARCHAR(255) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_accounts_platform_handle UNIQUE (platform, handle)
);
