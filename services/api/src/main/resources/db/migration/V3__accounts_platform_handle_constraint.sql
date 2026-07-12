ALTER TABLE accounts DROP CONSTRAINT uq_accounts_platform_handle;

CREATE UNIQUE INDEX uq_accounts_platform_handle ON accounts (platform, handle)
    WHERE platform <> 'MASTODON';
