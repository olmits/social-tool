CREATE TABLE drafts (
    id                  UUID PRIMARY KEY,
    account_id          UUID         NOT NULL,
    signal_id           UUID,
    platform            VARCHAR(20)  NOT NULL,
    content             TEXT         NOT NULL,
    affiliate_links     TEXT,
    status              VARCHAR(20)  NOT NULL,
    ai_generated        BOOLEAN      NOT NULL,
    disclosure_included BOOLEAN      NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_drafts_account FOREIGN KEY (account_id) REFERENCES accounts (id)
);

-- signal_id has no FK yet: the signals table arrives in Phase 2 (trend radar).

CREATE INDEX idx_drafts_account ON drafts (account_id);
CREATE INDEX idx_drafts_status ON drafts (status);
