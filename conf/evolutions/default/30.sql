# --- !Ups
ALTER TABLE posting ADD COLUMN updated_by_author_id bigint;

# --- !Downs
ALTER TABLE posting DROP COLUMN updated_by_author_id;
