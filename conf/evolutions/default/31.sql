# --- !Ups

ALTER TABLE pull_request ADD COLUMN number BIGINT;
ALTER TABLE pull_request ADD CONSTRAINT uq_pull_request_1 UNIQUE (to_project_id,number);

# --- !Downs

ALTER TABLE pull_request DROP CONSTRAINT uq_pull_request_1;
ALTER TABLE pull_request DROP COLUMN number;
