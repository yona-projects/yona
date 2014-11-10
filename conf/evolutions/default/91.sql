# --- !Ups

ALTER TABLE pull_request DROP COLUMN conflict_files;

# --- !Downs

ALTER TABLE pull_request ADD COLUMN conflict_files clob;
