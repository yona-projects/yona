# --- !Ups
create table organization (
  id                        bigint not null,
  name                      varchar(255),
  descr                     varchar(255),
  created                   timestamp,
  constraint pk_organization primary key (id)
);
create sequence organization_seq;

create table organization_user (
  id                        bigint not null,
  user_id                   bigint,
  organization_id           bigint,
  role_id                   bigint,
  constraint pk_organization_user primary key (id))
;
create sequence organization_user_seq;
alter table organization_user add constraint fk_organization_user_user foreign key (user_id) references n4user (id) on delete restrict on update restrict;
create index ix_organization_user_user on organization_user (user_id);
alter table organization_user add constraint fk_organization_user_organization foreign key (organization_id) references organization (id) on delete restrict on update restrict;
create index ix_organization_user_organization on organization_user (organization_id);
alter table organization_user add constraint fk_organization_user_role foreign key (role_id) references role (id) on delete restrict on update restrict;
create index ix_organization_user_role on organization_user (role_id);

alter table project add column organization_id bigint;
alter table project add constraint fk_project_organization foreign key (organization_id) references organization (id) on delete restrict on update restrict;

alter table project add column project_scope varchar(255);
alter table project add constraint ck_project_project_scope check (project_scope in ('PRIVATE','PROTECTED','PUBLIC'));

update project set project_scope = 'PUBLIC' where is_public = true;
update project set project_scope = 'PRIVATE' where is_public = false;

insert into role (id, name, active) values (6, 'org_admin', true);
insert into role (id, name, active) values (7, 'org_member', true);
# --- !Downs
alter table project drop constraint if exists ck_project_project_scope;
alter table project drop column project_scope;

alter table project drop constraint if exists fk_project_organization;
alter table project drop column organization_id;

alter table organization_user drop constraint if exists fk_organization_user_role;
alter table organization_user drop constraint if exists fk_organization_user_organization;
alter table organization_user drop constraint if exists fk_organization_user_user;

drop sequence if exists organization_user_seq;
drop table if exists organization_user;

drop sequence if exists organization_seq;
drop table if exists organization;

delete from role where id = 6;
delete from role where id = 7;
