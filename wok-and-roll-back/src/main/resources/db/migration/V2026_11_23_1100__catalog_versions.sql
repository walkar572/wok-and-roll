-- Refs #178. Optimistic locking for the admin catalog (api-contract.md section 3.15):
--   UPDATE ... SET ..., version = version + 1 WHERE id = :id AND version = :version
-- No row updated means somebody else changed the row first: 409 VERSION_CONFLICT.
-- Two admins can never overwrite each other silently.

ALTER TABLE category ADD COLUMN version INT NOT NULL DEFAULT 0;
ALTER TABLE product ADD COLUMN version INT NOT NULL DEFAULT 0;
ALTER TABLE regional_price ADD COLUMN version INT NOT NULL DEFAULT 0;
