# --- !Ups
ALTER TABLE issue ADD COLUMN is_draft tinyint(1) default 0;
CREATE index ix_issue_is_draft_1 ON issue (weight, is_draft, number, created_date);
CREATE index ix_issue_is_draft_2 ON issue (is_draft, author_login_id, project_id);

# --- !Downs
drop index ix_issue_is_draft_1 on issue;
drop index ix_issue_is_draft_2 on issue;
ALTER TABLE issue DROP COLUMN is_draft;

