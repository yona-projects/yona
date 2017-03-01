# --- !Ups
ALTER TABLE issue ADD COLUMN parent_id bigint;
alter table issue add constraint fk_issue_parent_id_01 foreign key (parent_id) references issue (id) on delete set null;
CREATE INDEX ix_issue_parent_id ON issue (parent_id);

ALTER TABLE posting ADD COLUMN parent_id bigint;
alter table posting add constraint fk_posting_parent_id_01 foreign key (parent_id) references posting (id) on delete set null;
CREATE INDEX ix_posting_parent_id ON posting (parent_id);

# --- !Downs


ALTER TABLE issue DROP FOREIGN KEY fk_issue_parent_id_01;
ALTER TABLE posting DROP FOREIGN KEY fk_posting_parent_id_01;
ALTER TABLE issue DROP COLUMN parent_id;
ALTER TABLE posting DROP COLUMN parent_id;

