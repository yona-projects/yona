# --- !Ups

ALTER TABLE pull_request DROP COLUMN patch;

# --- !Downs

ALTER TABLE pull_request ADD COLUMN patch clob;
