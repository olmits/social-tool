CREATE TABLE mastodon_account_details (
    account_id UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    instance   VARCHAR(255) NOT NULL
);
