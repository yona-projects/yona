# --- !Ups
ALTER TABLE n4user ADD COLUMN token varchar(255);
CREATE UNIQUE INDEX uq_n4user_token ON n4user (token);

# --- !Downs
DROP INDEX IF EXISTS uq_n4user_token;
ALTER TABLE n4user DROP COLUMN token;
