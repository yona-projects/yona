# --- !Ups

ALTER TABLE pull_request_commit ALTER COLUMN commit_message clob;

# --- !Downs

ALTER TABLE pull_request_commit ALTER COLUMN commit_message varchar(2000);
