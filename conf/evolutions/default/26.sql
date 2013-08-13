# --- !Ups

ALTER TABLE pull_request ADD COLUMN merged_commit_id_from varchar(255);
ALTER TABLE pull_request ADD COLUMN merged_commit_id_to varchar(255);

# --- !Downs

ALTER TABLE pull_request DROP COLUMN merged_commit_id_from;
ALTER TABLE pull_request DROP COLUMN merged_commit_id_to;
