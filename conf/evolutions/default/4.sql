# --- !Ups

create table recent_project (
  id                        bigint auto_increment not null,
  user_id                   bigint,
  owner                     varchar(255),
  project_id                bigint,
  project_name              varchar(255),
  constraint pk_recent_project primary key (id),
  constraint uq_recent_project_1 unique (user_id, project_id))
  row_format=compressed, key_block_size=8
;

alter table recent_project add constraint fk_recent_project_project_2 foreign key (project_id) references project (id) on delete CASCADE on update CASCADE;

# --- !Downs
drop table recent_project;
