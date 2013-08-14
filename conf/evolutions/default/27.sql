# --- !Ups

create table watch (
  id                        bigint not null,
  user_id                   bigint,
  resource_type             varchar(16),
  resource_id               varchar(255),
  constraint ck_watch_resource_type check (resource_type in ('ISSUE_POST','ISSUE_ASSIGNEE','ISSUE_STATE','ISSUE_CATEGORY','ISSUE_MILESTONE','ISSUE_LABEL','BOARD_POST','BOARD_CATEGORY','BOARD_NOTICE','CODE','MILESTONE','WIKI_PAGE','PROJECT_SETTING','SITE_SETTING','USER','USER_AVATAR','PROJECT','ATTACHMENT','ISSUE_COMMENT','NONISSUE_COMMENT','LABEL','PROJECT_LABELS','FORK','CODE_COMMENT','PULL_REQUEST','SIMPLE_COMMENT', 'COMMIT')),
  constraint pk_watch primary key (id))
;

create table unwatch (
  id                        bigint not null,
  user_id                   bigint,
  resource_type             varchar(16),
  resource_id               varchar(255),
  constraint ck_unwatch_resource_type check (resource_type in ('ISSUE_POST','ISSUE_ASSIGNEE','ISSUE_STATE','ISSUE_CATEGORY','ISSUE_MILESTONE','ISSUE_LABEL','BOARD_POST','BOARD_CATEGORY','BOARD_NOTICE','CODE','MILESTONE','WIKI_PAGE','PROJECT_SETTING','SITE_SETTING','USER','USER_AVATAR','PROJECT','ATTACHMENT','ISSUE_COMMENT','NONISSUE_COMMENT','LABEL','PROJECT_LABELS','FORK','CODE_COMMENT','PULL_REQUEST','SIMPLE_COMMENT', 'COMMIT')),
  constraint pk_unwatch primary key (id))
;

create sequence watch_seq;

create sequence unwatch_seq;

alter table unwatch add constraint fk_unwatch_unwatcher_23 foreign key (user_id) references n4user (id) on delete restrict on update restrict;

create index ix_unwatch_unwatcher_23 on unwatch (user_id);

alter table watch add constraint fk_watch_watcher_24 foreign key (user_id) references n4user (id) on delete restrict on update restrict;

create index ix_watch_watcher_24 on watch (user_id);

drop table if exists user_watching_project;

drop table if exists commit_explicit_watching;

drop table if exists commit_explicit_watcher;

drop table if exists commit_explicit_unwatcher;

drop sequence if exists commit_explicit_watching_seq;

ALTER TABLE attachment ALTER COLUMN container_id TYPE varchar(255);

# --- !Downs

drop table if exists unwatch;

drop table if exists watch;

drop sequence if exists unwatch_seq;

drop sequence if exists watch_seq;

ALTER TABLE attachment ALTER COLUMN container_id TYPE bigint;
