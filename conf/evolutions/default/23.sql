# --- !Ups

create table commit_explicit_watching (
  id                        bigint not null,
  project_id                bigint,
  commit_id                 varchar(255),
  constraint pk_commit_explicit_watching primary key (id))
;

create table commit_explicit_watcher (
  commit_explicit_watching_id    bigint not null,
  n4user_id                      bigint not null,
  constraint pk_commit_explicit_watcher primary key (commit_explicit_watching_id, n4user_id))
;

create table commit_explicit_unwatcher (
  commit_explicit_watching_id    bigint not null,
  n4user_id                      bigint not null,
  constraint pk_commit_explicit_unwatcher primary key (commit_explicit_watching_id, n4user_id))
;

create sequence commit_explicit_watching_seq;

alter table commit_explicit_watching add constraint fk_commit_explicit_watching_pr_4 foreign key (project_id) references project (id) on delete restrict on update restrict;

create index ix_commit_explicit_watching_pr_4 on commit_explicit_watching (project_id);

alter table commit_explicit_watcher add constraint fk_commit_explicit_watcher_co_01 foreign key (commit_explicit_watching_id) references commit_explicit_watching (id) on delete restrict on update restrict;

alter table commit_explicit_watcher add constraint fk_commit_explicit_watcher_n4_02 foreign key (n4user_id) references n4user (id) on delete restrict on update restrict;

alter table commit_explicit_unwatcher add constraint fk_commit_explicit_unwatcher__01 foreign key (commit_explicit_watching_id) references commit_explicit_watching (id) on delete restrict on update restrict;

alter table commit_explicit_unwatcher add constraint fk_commit_explicit_unwatcher__02 foreign key (n4user_id) references n4user (id) on delete restrict on update restrict;

ALTER TABLE notification_event DROP CONSTRAINT IF EXISTS ck_notification_event_resource_type;

ALTER TABLE notification_event ADD CONSTRAINT ck_notification_event_resource_type check (resource_type in ('ISSUE_POST','ISSUE_ASSIGNEE','ISSUE_STATE','ISSUE_CATEGORY','ISSUE_MILESTONE','ISSUE_LABEL','BOARD_POST','BOARD_CATEGORY','BOARD_NOTICE','CODE','MILESTONE','WIKI_PAGE','PROJECT_SETTING','SITE_SETTING','USER','USER_AVATAR','PROJECT','ATTACHMENT','ISSUE_COMMENT','NONISSUE_COMMENT','LABEL','PROJECT_LABELS','FORK', 'CODE_COMMENT'));

# --- !Downs

drop table if exists commit_explicit_watching;

drop table if exists commit_explicit_watcher;

drop table if exists commit_explicit_unwatcher;

drop sequence if exists commit_explicit_watching_seq;

ALTER TABLE notification_event DROP CONSTRAINT IF EXISTS ck_notification_event_resource_type;

ALTER TABLE notification_event ADD CONSTRAINT ck_notification_event_resource_type check (resource_type in ('ISSUE_POST','ISSUE_ASSIGNEE','ISSUE_STATE','ISSUE_CATEGORY','ISSUE_MILESTONE','ISSUE_LABEL','BOARD_POST','BOARD_CATEGORY','BOARD_NOTICE','CODE','MILESTONE','WIKI_PAGE','PROJECT_SETTING','SITE_SETTING','USER','USER_AVATAR','PROJECT','ATTACHMENT','ISSUE_COMMENT','NONISSUE_COMMENT','LABEL','PROJECT_LABELS','FORK'));
