# --- !Ups
ALTER TABLE issue_comment ADD COLUMN parent_comment_id bigint;
alter table issue_comment add constraint fk_issue_comment_parent_id_01 foreign key (parent_comment_id) references issue_comment (id) on delete set null;
CREATE INDEX ix_issue_parent_id ON issue_comment (parent_comment_id);

ALTER TABLE posting_comment ADD COLUMN parent_comment_id bigint;
alter table posting_comment add constraint fk_posting_comment_parent_id_01 foreign key (parent_comment_id) references posting_comment (id) on delete set null;
CREATE INDEX ix_posting_parent_id ON posting_comment (parent_comment_id);

# --- !Downs
ALTER TABLE issue_comment DROP FOREIGN KEY fk_issue_comment_parent_id_01;
ALTER TABLE issue_comment DROP COLUMN parent_comment_id;

ALTER TABLE posting_comment DROP FOREIGN KEY fk_posting_comment_parent_id_01;
ALTER TABLE posting_comment DROP COLUMN parent_comment_id;

