# --- !Ups
SET FOREIGN_KEY_CHECKS=0;

ALTER TABLE issue_comment ADD COLUMN project_id bigint;
DELETE FROM issue_comment WHERE issue_id = 0;
COMMIT;

UPDATE issue_comment a
SET a.project_id = (SELECT project_id FROM issue b WHERE a.issue_id = b.id);

ALTER TABLE issue_comment MODIFY COLUMN project_id bigint NOT NULL;

CREATE INDEX ix_issue_comment_project_id ON issue_comment (project_id);

ALTER TABLE posting_comment ADD COLUMN project_id bigint NOT NULL;
DELETE FROM posting_comment WHERE posting_id = 0;
COMMIT;

UPDATE posting_comment a
SET a.project_id = (SELECT project_id FROM posting b WHERE a.posting_id = b.id);
ALTER TABLE posting_comment MODIFY COLUMN project_id bigint NOT NULL;

SET FOREIGN_KEY_CHECKS=1;

CREATE INDEX ix_posting_comment_project_id ON posting_comment (project_id);

CREATE INDEX ix_issue_comment_author_id ON issue_comment (author_id);
CREATE INDEX ix_posting_comment_author_id ON posting_comment (author_id);
CREATE INDEX ix_pull_request_number ON pull_request (number);
CREATE INDEX ix_issue_author_id_state ON issue (author_id, state);
CREATE INDEX ix_issue_created_date ON issue (created_date);

CREATE INDEX ix_n4user_email ON n4user (email);
CREATE UNIQUE INDEX uq_email_email_valid ON email (email, valid);

# --- !Downs
DROP INDEX ix_issue_comment_project_id ON issue_comment;
DROP INDEX ix_posting_comment_project_id ON posting_comment;
ALTER TABLE issue_comment DROP COLUMN project_id;
ALTER TABLE posting_comment DROP COLUMN project_id;

DROP INDEX ix_issue_comment_author_id ON issue_comment;
DROP INDEX ix_posting_comment_author_id ON posting_comment;
DROP INDEX ix_pull_request_number ON pull_request;
DROP INDEX ix_issue_author_id_state ON issue;
DROP INDEX ix_issue_created_date ON issue;

DROP INDEX ix_n4user_email ON n4user;
DROP  INDEX uq_email_email_valid ON email;
