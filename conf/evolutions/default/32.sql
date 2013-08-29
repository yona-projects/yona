# --- !Ups

create table user_project_notification (
  id                        bigint not null,
  user_id                   bigint,
  project_id                bigint,
  notification_type         varchar(255),
  allowed                   boolean,

  constraint pk_user_project_notification primary key (id))
;

create sequence user_project_notification_seq;

alter table user_project_notification add constraint fk_user_project_notification_user_25 foreign key (user_id) references n4user (id) on delete restrict on update restrict;

create index ix_user_project_notification_user_25 on user_project_notification (user_id);

alter table user_project_notification add constraint fk_user_project_notification_project_26 foreign key (project_id) references project (id) on delete restrict on update restrict;

create index ix_user_project_notification_project_26 on user_project_notification (project_id);


# --- !Downs

drop table if exists user_project_notification;

drop sequence if exists user_project_notification_seq;
