# --- !Ups
ALTER TABLE issue ADD COLUMN updated_by_author_id bigint;
ALTER TABLE posting ADD COLUMN updated_by_author_id bigint;

# --- !Downs
ALTER TABLE issue DROP COLUMN updated_by_author_id;
ALTER TABLE posting DROP COLUMN updated_by_author_id;

