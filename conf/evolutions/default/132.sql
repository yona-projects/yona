# --- !Ups
ALTER TABLE issue ADD COLUMN is_draft tinyint(1) default 0;
CREATE index ix_issue_is_draft_1 ON issue (weight, is_draft, number, created_date);

# --- !Downs
drop index ix_issue_is_draft_1 on issue;
ALTER TABLE issue DROP COLUMN is_draft;

