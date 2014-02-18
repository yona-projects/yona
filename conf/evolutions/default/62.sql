# --- !Ups
create table recently_visited_projects (
  id                        bigint not null,
  user_id                   bigint not null,
  constraint pk_recently_visited_projects primary key (id)
);
create sequence recently_visited_projects_seq;
create index ix_recently_visited_projects_user on recently_visited_projects (user_id);
alter table recently_visited_projects add constraint fk_user_id foreign key (user_id) references n4user (id) on delete restrict on update restrict;

create table project_visitation (
  id                            bigint not null,
  visited                       timestamp,
  project_id                    bigint not null,
  recently_visited_projects_id  bigint not null,
  constraint pk_project_visitation primary key (id)
);
create sequence project_visitation_seq;
alter table project_visitation add constraint fk_project_visit_project_id foreign key (project_id) references project (id) on delete restrict on update restrict;
alter table project_visitation add constraint fk_recently_visited_projects_id foreign key (recently_visited_projects_id) references recently_visited_projects (id) on delete restrict on update restrict;

# --- !Downs
alter table project_visitation drop constraint if exists fk_project_visit_project_id;
alter table project_visitation drop constraint if exists fk_recently_visited_projects_id;
drop sequence if exists project_visitation_seq;
drop table if exists project_visitation;

alter table recently_visited_projects drop constraint if exists fk_user_id;
drop sequence if exists recently_visited_projects_seq;
drop table if exists recently_visited_projects;
