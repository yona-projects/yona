# --- !Ups
ALTER TABLE issue ADD COLUMN updated_by_author_id bigint;

# --- !Downs
ALTER TABLE issue DROP COLUMN updated_by_author_id;
