# --- !Ups
ALTER TABLE issue DROP FOREIGN KEY fk_issue_assignee_9;
ALTER TABLE issue ADD CONSTRAINT fk_issue_assignee_9 FOREIGN KEY (assignee_id) REFERENCES assignee (id) ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE issue DROP FOREIGN KEY fk_issue_project_7;
ALTER TABLE issue ADD CONSTRAINT fk_issue_project_7 FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE ON UPDATE CASCADE;

# --- !Downs
