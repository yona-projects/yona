# --- !Ups

create table assignee (
  id                        bigint not null,
  user_id                   bigint,
  project_id                bigint,
  constraint pk_assignee primary key (id))
;

create table attachment (
  id                        bigint not null,
  name                      varchar(255),
  hash                      varchar(255),
  project_id                bigint,
  container_type            varchar(15),
  mime_type                 varchar(255),
  size                      bigint,
  container_id              bigint,
  constraint ck_attachment_container_type check (container_type in ('ISSUE_POST','ISSUE_COMMENT','ISSUE_ASSIGNEE','ISSUE_STATE','ISSUE_CATEGORY','ISSUE_MILESTONE','ISSUE_NOTICE','ISSUE_LABEL','BOARD_POST','BOARD_COMMENT','BOARD_CATEGORY','BOARD_NOTICE','CODE','MILESTONE','WIKI_PAGE','PROJECT_SETTING','SITE_SETTING','USER','USER_AVATAR','PROJECT','ATTACHMENT')),
  constraint pk_attachment primary key (id))
;

create table card (
  id                        bigint not null,
  title                     varchar(255),
  checklist_id              bigint,
  line_id                   bigint,
  story_point               integer,
  body                      varchar(255),
  due_date                  timestamp,
  constraint pk_card primary key (id))
;

create table card_assignee (
  id                        bigint not null,
  card_id                   bigint,
  project_user_id           bigint,
  constraint pk_card_assignee primary key (id))
;

create table card_label (
  id                        bigint not null,
  card_id                   bigint,
  label_id                  bigint,
  constraint pk_card_label primary key (id))
;

create table checklist (
  id                        bigint not null,
  title                     varchar(255),
  constraint pk_checklist primary key (id))
;

create table comment (
  id                        bigint not null,
  contents                  varchar(4000),
  date                      timestamp,
  file_path                 varchar(255),
  author_id                 bigint,
  author_login_id           varchar(255),
  author_name               varchar(255),
  post_id                   bigint,
  constraint pk_comment primary key (id))
;

create table issue (
  id                        bigint not null,
  title                     varchar(255),
  body                      clob,
  date                      timestamp,
  num_of_comments           integer,
  milestone_id              bigint,
  author_id                 bigint,
  author_login_id           varchar(255),
  author_name               varchar(255),
  state                     integer,
  project_id                bigint,
  assignee_id               bigint,
  constraint ck_issue_state check (state in (0,1,2)),
  constraint pk_issue primary key (id))
;

create table issue_comment (
  id                        bigint not null,
  contents                  varchar(4000),
  date                      timestamp,
  author_id                 bigint,
  author_login_id           varchar(255),
  author_name               varchar(255),
  file_path                 varchar(255),
  issue_id                  bigint,
  constraint pk_issue_comment primary key (id))
;

create table issue_label (
  id                        bigint not null,
  category                  varchar(255),
  color                     varchar(255),
  name                      varchar(255),
  project_id                bigint,
  constraint pk_issue_label primary key (id))
;

create table item (
  id                        bigint not null,
  state                     boolean,
  body                      varchar(255),
  checklist_id              bigint,
  constraint pk_item primary key (id))
;

create table label (
  id                        bigint not null,
  name                      varchar(255),
  color                     varchar(255),
  task_board_id             bigint,
  constraint pk_label primary key (id))
;

create table line (
  id                        bigint not null,
  title                     varchar(255),
  task_board_id             bigint,
  constraint pk_line primary key (id))
;

create table milestone (
  id                        bigint not null,
  title                     varchar(255),
  due_date                  timestamp,
  contents                  clob,
  state                     integer,
  project_id                bigint,
  constraint ck_milestone_state check (state in (0,1,2)),
  constraint pk_milestone primary key (id))
;

create table post (
  id                        bigint not null,
  title                     varchar(255),
  contents                  clob,
  date                      timestamp,
  comment_count             integer,
  file_path                 varchar(255),
  author_id                 bigint,
  author_login_id           varchar(255),
  author_name               varchar(255),
  project_id                bigint,
  constraint pk_post primary key (id))
;

create table project (
  id                        bigint not null,
  name                      varchar(255),
  overview                  varchar(255),
  vcs                       varchar(255),
  siteurl                   varchar(255),
  logo_path                 varchar(255),
  owner                     varchar(255),
  share_option              boolean,
  is_author_editable        boolean,
  date                      timestamp,
  constraint pk_project primary key (id))
;

create table project_user (
  id                        bigint not null,
  user_id                   bigint,
  project_id                bigint,
  role_id                   bigint,
  constraint pk_project_user primary key (id))
;

create table role (
  id                        bigint not null,
  name                      varchar(255),
  active                    boolean,
  constraint pk_role primary key (id))
;

create table site_admin (
  id                        bigint not null,
  admin_id                  bigint,
  constraint pk_site_admin primary key (id))
;

create table task_board (
  id                        bigint not null,
  project_id                bigint,
  constraint pk_task_board primary key (id))
;

create table task_comment (
  id                        bigint not null,
  body                      varchar(255),
  card_id                   bigint,
  constraint pk_task_comment primary key (id))
;

create table n4user (
  id                        bigint not null,
  name                      varchar(255),
  login_id                  varchar(255),
  password                  varchar(255),
  password_salt             varchar(255),
  email                     varchar(255),
  avatar_url                varchar(255),
  remember_me               boolean,
  date                      timestamp,
  constraint pk_n4user primary key (id))
;


create table issue_issue_label (
  issue_id                       bigint not null,
  issue_label_id                 bigint not null,
  constraint pk_issue_issue_label primary key (issue_id, issue_label_id))
;
create sequence assignee_seq;

create sequence attachment_seq;

create sequence card_seq;

create sequence card_assignee_seq;

create sequence card_label_seq;

create sequence checklist_seq;

create sequence comment_seq;

create sequence issue_seq;

create sequence issue_comment_seq;

create sequence issue_label_seq;

create sequence item_seq;

create sequence label_seq;

create sequence line_seq;

create sequence milestone_seq;

create sequence post_seq;

create sequence project_seq;

create sequence project_user_seq;

create sequence role_seq;

create sequence site_admin_seq;

create sequence task_board_seq;

create sequence task_comment_seq;

create sequence n4user_seq;

alter table assignee add constraint fk_assignee_user_1 foreign key (user_id) references n4user (id) on delete restrict on update restrict;
create index ix_assignee_user_1 on assignee (user_id);
alter table assignee add constraint fk_assignee_project_2 foreign key (project_id) references project (id) on delete restrict on update restrict;
create index ix_assignee_project_2 on assignee (project_id);
alter table card add constraint fk_card_checklist_3 foreign key (checklist_id) references checklist (id) on delete restrict on update restrict;
create index ix_card_checklist_3 on card (checklist_id);
alter table card add constraint fk_card_line_4 foreign key (line_id) references line (id) on delete restrict on update restrict;
create index ix_card_line_4 on card (line_id);
alter table card_assignee add constraint fk_card_assignee_card_5 foreign key (card_id) references card (id) on delete restrict on update restrict;
create index ix_card_assignee_card_5 on card_assignee (card_id);
alter table card_assignee add constraint fk_card_assignee_projectUser_6 foreign key (project_user_id) references project_user (id) on delete restrict on update restrict;
create index ix_card_assignee_projectUser_6 on card_assignee (project_user_id);
alter table card_label add constraint fk_card_label_card_7 foreign key (card_id) references card (id) on delete restrict on update restrict;
create index ix_card_label_card_7 on card_label (card_id);
alter table card_label add constraint fk_card_label_label_8 foreign key (label_id) references label (id) on delete restrict on update restrict;
create index ix_card_label_label_8 on card_label (label_id);
alter table comment add constraint fk_comment_post_9 foreign key (post_id) references post (id) on delete restrict on update restrict;
create index ix_comment_post_9 on comment (post_id);
alter table issue add constraint fk_issue_project_10 foreign key (project_id) references project (id) on delete restrict on update restrict;
create index ix_issue_project_10 on issue (project_id);
alter table issue add constraint fk_issue_assignee_11 foreign key (assignee_id) references assignee (id) on delete restrict on update restrict;
create index ix_issue_assignee_11 on issue (assignee_id);
alter table issue_comment add constraint fk_issue_comment_issue_12 foreign key (issue_id) references issue (id) on delete restrict on update restrict;
create index ix_issue_comment_issue_12 on issue_comment (issue_id);
alter table issue_label add constraint fk_issue_label_project_13 foreign key (project_id) references project (id) on delete restrict on update restrict;
create index ix_issue_label_project_13 on issue_label (project_id);
alter table item add constraint fk_item_checklist_14 foreign key (checklist_id) references checklist (id) on delete restrict on update restrict;
create index ix_item_checklist_14 on item (checklist_id);
alter table label add constraint fk_label_taskBoard_15 foreign key (task_board_id) references task_board (id) on delete restrict on update restrict;
create index ix_label_taskBoard_15 on label (task_board_id);
alter table line add constraint fk_line_taskBoard_16 foreign key (task_board_id) references task_board (id) on delete restrict on update restrict;
create index ix_line_taskBoard_16 on line (task_board_id);
alter table milestone add constraint fk_milestone_project_17 foreign key (project_id) references project (id) on delete restrict on update restrict;
create index ix_milestone_project_17 on milestone (project_id);
alter table post add constraint fk_post_project_18 foreign key (project_id) references project (id) on delete restrict on update restrict;
create index ix_post_project_18 on post (project_id);
alter table project_user add constraint fk_project_user_user_19 foreign key (user_id) references n4user (id) on delete restrict on update restrict;
create index ix_project_user_user_19 on project_user (user_id);
alter table project_user add constraint fk_project_user_project_20 foreign key (project_id) references project (id) on delete restrict on update restrict;
create index ix_project_user_project_20 on project_user (project_id);
alter table project_user add constraint fk_project_user_role_21 foreign key (role_id) references role (id) on delete restrict on update restrict;
create index ix_project_user_role_21 on project_user (role_id);
alter table site_admin add constraint fk_site_admin_admin_22 foreign key (admin_id) references n4user (id) on delete restrict on update restrict;
create index ix_site_admin_admin_22 on site_admin (admin_id);
alter table task_board add constraint fk_task_board_project_23 foreign key (project_id) references project (id) on delete restrict on update restrict;
create index ix_task_board_project_23 on task_board (project_id);
alter table task_comment add constraint fk_task_comment_card_24 foreign key (card_id) references card (id) on delete restrict on update restrict;
create index ix_task_comment_card_24 on task_comment (card_id);



alter table issue_issue_label add constraint fk_issue_issue_label_issue_01 foreign key (issue_id) references issue (id) on delete restrict on update restrict;

alter table issue_issue_label add constraint fk_issue_issue_label_issue_la_02 foreign key (issue_label_id) references issue_label (id) on delete restrict on update restrict;

# --- !Downs

SET REFERENTIAL_INTEGRITY FALSE;

drop table if exists assignee;

drop table if exists attachment;

drop table if exists card;

drop table if exists card_assignee;

drop table if exists card_label;

drop table if exists checklist;

drop table if exists comment;

drop table if exists issue;

drop table if exists issue_issue_label;

drop table if exists issue_comment;

drop table if exists issue_label;

drop table if exists item;

drop table if exists label;

drop table if exists line;

drop table if exists milestone;

drop table if exists post;

drop table if exists project;

drop table if exists project_user;

drop table if exists role;

drop table if exists site_admin;

drop table if exists task_board;

drop table if exists task_comment;

drop table if exists n4user;

SET REFERENTIAL_INTEGRITY TRUE;

drop sequence if exists assignee_seq;

drop sequence if exists attachment_seq;

drop sequence if exists card_seq;

drop sequence if exists card_assignee_seq;

drop sequence if exists card_label_seq;

drop sequence if exists checklist_seq;

drop sequence if exists comment_seq;

drop sequence if exists issue_seq;

drop sequence if exists issue_comment_seq;

drop sequence if exists issue_label_seq;

drop sequence if exists item_seq;

drop sequence if exists label_seq;

drop sequence if exists line_seq;

drop sequence if exists milestone_seq;

drop sequence if exists post_seq;

drop sequence if exists project_seq;

drop sequence if exists project_user_seq;

drop sequence if exists role_seq;

drop sequence if exists site_admin_seq;

drop sequence if exists task_board_seq;

drop sequence if exists task_comment_seq;

drop sequence if exists n4user_seq;

