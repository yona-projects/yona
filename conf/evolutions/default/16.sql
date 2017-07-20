# --- !Ups
ALTER TABLE n4user ADD COLUMN is_guest tinyint(1) default 0;
CREATE INDEX ix_n4user_is_guest ON n4user (is_guest);

# --- !Downs
DROP INDEX IF EXISTS ix_n4user_is_guest ON n4user;
ALTER TABLE n4user DROP COLUMN is_guest;