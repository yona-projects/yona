# --- !Ups
create table project_pushed_branch (
  id                        bigint not null,
  pushed_date               timestamp,
  name                    varchar(255),
  project_id                bigint not null,
  constraint pk_pushed_branch primary key (id)
);

create sequence project_pushed_branch_seq;
alter table project_pushed_branch add constraint fk_project_pushed_branch_project foreign key (project_id) references project (id) on delete restrict on update restrict;

# --- !Downs
drop sequence if exists project_pushed_branch_seq;
drop table if exists project_pushed_branch;
