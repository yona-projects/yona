# --- !Ups

ALTER TABLE project ADD COLUMN last_issue_number BIGINT;
ALTER TABLE project ADD COLUMN last_posting_number BIGINT;
ALTER TABLE issue ADD COLUMN number BIGINT;
ALTER TABLE posting ADD COLUMN number BIGINT;
ALTER TABLE issue ADD CONSTRAINT uq_posting_1 UNIQUE (project_id,number);
ALTER TABLE posting ADD CONSTRAINT uq_issue_1 UNIQUE (project_id,number);

# --- !Downs

ALTER TABLE project DROP COLUMN last_issue_number;
ALTER TABLE project DROP COLUMN last_posting_number;
ALTER TABLE issue DROP COLUMN number;
ALTER TABLE posting DROP COLUMN number;
ALTER TABLE issue DROP CONSTRAINT uq_posting_1;
ALTER TABLE posting DROP CONSTRAINT uq_issue_1;
