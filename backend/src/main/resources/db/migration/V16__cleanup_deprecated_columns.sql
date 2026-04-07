-- V16: Remove deprecated columns from virtual_accounts
ALTER TABLE virtual_accounts DROP COLUMN balance_krw;
ALTER TABLE virtual_accounts DROP COLUMN locked_krw;
