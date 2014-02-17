# --- !Ups
create table issue_voter (
    issue_id BIGINT NOT NULL,
    user_id INT NOT NULL
);

alter table issue_voter add constraint fk_issue_voter_1 foreign key (issue_id) references issue (id) on delete restrict on update restrict;
alter table issue_voter add constraint fk_issue_voter_2 foreign key (user_id) references n4user (id) on delete restrict on update restrict;

ALTER TABLE issue_event DROP CONSTRAINT IF EXISTS ck_issue_event_event_type;
ALTER TABLE issue_event ADD CONSTRAINT ck_issue_event_event_type check (event_type in ('NEW_ISSUE','NEW_POSTING','ISSUE_ASSIGNEE_CHANGED','ISSUE_STATE_CHANGED','NEW_COMMENT','NEW_COMMIT_COMMENT','NEW_PULL_REQUEST','NEW_PULL_REQUEST_COMMENT','PULL_REQUEST_STATE_CHANGED','ISSUE_REFERRED_FROM_COMMIT','ISSUE_REFERRED_FROM_PULL_REQUEST', 'ISSUE_BODY_CHANGED'));
# --- !Downs
alter table issue_voter drop constraint if exists fk_issue_voter_1;
alter table issue_voter drop constraint if exists fk_issue_voter_2;
drop table if exists issue_voter;

ALTER TABLE issue_event DROP CONSTRAINT IF EXISTS ck_issue_event_event_type;
ALTER TABLE issue_event ADD CONSTRAINT ck_issue_event_event_type check (event_type in ('NEW_ISSUE','NEW_POSTING','ISSUE_ASSIGNEE_CHANGED','ISSUE_STATE_CHANGED','NEW_COMMENT','NEW_COMMIT_COMMENT','NEW_PULL_REQUEST','NEW_PULL_REQUEST_COMMENT','PULL_REQUEST_STATE_CHANGED','ISSUE_REFERRED_FROM_COMMIT','ISSUE_REFERRED_FROM_PULL_REQUEST'));
