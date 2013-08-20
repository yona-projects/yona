# --- !Ups

ALTER TABLE project ADD COLUMN last_pull_request_number BIGINT;
ALTER TABLE pull_request ADD COLUMN number BIGINT;
ALTER TABLE pull_request ADD CONSTRAINT uq_pull_request_1 UNIQUE (to_project_id,number);
UPDATE project SET last_pull_request_number = 0 WHERE last_pull_request_number IS NULL;

# --- !Downs

ALTER TABLE pull_request DROP CONSTRAINT uq_pull_request_1;
ALTER TABLE project DROP COLUMN last_pull_request_number;
ALTER TABLE pull_request DROP COLUMN number;
