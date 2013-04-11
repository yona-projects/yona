# --- !Ups

UPDATE project SET last_issue_number = 0 WHERE last_issue_number IS NULL;
UPDATE project SET last_posting_number = 0 WHERE last_posting_number IS NULL;
