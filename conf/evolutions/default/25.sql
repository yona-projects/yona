# --- !Ups

ALTER TABLE pull_request ADD COLUMN last_commit_id varchar(255);

# --- !Downs

ALTER TABLE pull_request DROP COLUMN last_commit_id;
