# --- !Ups
create table user_enrolled_project (
  user_id                        bigint not null,
  project_id                bigint not null,
  constraint pk_user_enrolled_project primary key (user_id, project_id));

# --- !Downs

drop table if exists user_enrolled_project;
