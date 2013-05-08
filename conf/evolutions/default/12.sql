# --- !Ups
ALTER TABLE project ALTER COLUMN share_option RENAME TO is_public;

# --- !Downs
ALTER TABLE project ALTER COLUMN is_public RENAME TO share_option;
