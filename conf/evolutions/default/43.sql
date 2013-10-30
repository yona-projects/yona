# --- !Ups

ALTER TABLE pull_request ALTER COLUMN conflict_files clob;

# --- !Downs

ALTER TABLE pull_request ALTER COLUMN conflict_files varchar(255);
