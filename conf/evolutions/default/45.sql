# --- !Ups
ALTER TABLE attachment DROP COLUMN project_id;

# --- !Downs
ALTER TABLE attachment ADD COLUMN project_id bigint;
