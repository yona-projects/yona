# --- !Ups

ALTER TABLE pull_request_comment DROP COLUMN IF EXISTS avatar_url;

# --- !Downs

ALTER TABLE pull_request_comment ADD COLUMN avatar_url varchar(255);
