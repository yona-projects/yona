# --- !Ups

create table code_comment (
  id                        bigint not null,
  project_id                bigint,
  commit_id                 varchar(255),
  path                      varchar(255),
  line                      bigint,
  side                      varchar(255),
  contents                  varchar(4000),
  created_date              timestamp,
  author_id                 bigint,
  author_login_id           varchar(255),
  author_name               varchar(255),
  constraint pk_code_comment primary key (id))
;

create sequence code_comment_seq;

alter table code_comment add constraint fk_code_comment_project_3 foreign key (project_id) references project (id) on delete restrict on update restrict;

create index ix_code_comment_project_3 on code_comment (project_id);

ALTER TABLE attachment DROP CONSTRAINT IF EXISTS ck_attachment_container_type;
ALTER TABLE attachment ADD CONSTRAINT ck_attachment_container_type check (container_type in ('ISSUE_POST','ISSUE_ASSIGNEE','ISSUE_STATE','ISSUE_CATEGORY','ISSUE_MILESTONE','ISSUE_LABEL','BOARD_POST','BOARD_CATEGORY','BOARD_NOTICE','CODE','MILESTONE','WIKI_PAGE','PROJECT_SETTING','SITE_SETTING','USER','USER_AVATAR','PROJECT','ATTACHMENT','ISSUE_COMMENT','NONISSUE_COMMENT','CODE_COMMENT'));

# --- !Downs

drop table if exists code_comment;
drop sequence if exists code_comment_seq;

DELETE FROM attachment WHERE container_type='CODE_COMMENT';

ALTER TABLE attachment DROP CONSTRAINT IF EXISTS ck_attachment_container_type;
ALTER TABLE attachment ADD CONSTRAINT ck_attachment_container_type check (container_type in ('ISSUE_POST','ISSUE_ASSIGNEE','ISSUE_STATE','ISSUE_CATEGORY','ISSUE_MILESTONE','ISSUE_LABEL','BOARD_POST','BOARD_CATEGORY','BOARD_NOTICE','CODE','MILESTONE','WIKI_PAGE','PROJECT_SETTING','SITE_SETTING','USER','USER_AVATAR','PROJECT','ATTACHMENT','ISSUE_COMMENT','NONISSUE_COMMENT'));
