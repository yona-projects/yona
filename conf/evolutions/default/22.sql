# --- !Ups

create table notification_event (
  id                        bigint not null,
  title                     varchar(255),
  message                   clob,
  sender_id                 bigint,
  created                   timestamp,
  url_to_view               varchar(255),
  resource_type             varchar(16),
  resource_id               bigint,
  type                      varchar(255),
  old_value                 clob,
  new_value                 clob,
  constraint ck_notification_event_resource_type check (resource_type in ('ISSUE_POST','ISSUE_ASSIGNEE','ISSUE_STATE','ISSUE_CATEGORY','ISSUE_MILESTONE','ISSUE_LABEL','BOARD_POST','BOARD_CATEGORY','BOARD_NOTICE','CODE','MILESTONE','WIKI_PAGE','PROJECT_SETTING','SITE_SETTING','USER','USER_AVATAR','PROJECT','ATTACHMENT','ISSUE_COMMENT','NONISSUE_COMMENT','LABEL','PROJECT_LABELS','FORK')),
  constraint pk_notification_event primary key (id));

create table notification_event_n4user (
  notification_event_id          bigint not null,
  n4user_id                      bigint not null,
  constraint pk_notification_event_n4user primary key (notification_event_id, n4user_id));

create table notification_mail (
  id                        bigint not null,
  notification_event_id     bigint,
  constraint pk_notification_mail primary key (id))
;

create sequence notification_event_seq;

alter table notification_event_n4user add constraint fk_notification_event_n4user__01 foreign key (notification_event_id) references notification_event (id) on delete restrict on update restrict;

alter table notification_event_n4user add constraint fk_notification_event_n4user__02 foreign key (n4user_id) references n4user (id) on delete restrict on update restrict;

create sequence notification_mail_seq;

alter table notification_mail add constraint fk_notification_mail_notificat_9 foreign key (notification_event_id) references notification_event (id) on delete restrict on update restrict;

create index ix_notification_mail_notificat_9 on notification_mail (notification_event_id);

# --- !Downs

drop table if exists notification_event;

drop table if exists notification_event_n4user;

drop sequence if exists notification_event_seq;

drop table if exists notification_mail;

drop sequence if exists notification_mail_seq;
