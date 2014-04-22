# --- !Ups
create table project_transfer (
  id                        bigint not null,
  sender_id                 varchar(255),
  destination               varchar(255),
  project_id                bigint not null,
  requested                 timestamp,
  confirm_key               varchar(50),
  accepted                  boolean,
  new_project_name          varchar(255),
  constraint pk_project_transfer primary key (id)
);
create sequence project_transfer_seq;
create index ix_project_transfer_project on project_transfer (project_id);
alter table project_transfer add constraint fk_sender_id foreign key (sender_id) references n4user (id) on delete restrict on update restrict;
alter table project_transfer add constraint fk_project_id foreign key (project_id) references project (id) on delete restrict on update restrict;
# --- !Downs
alter table project_visitation drop constraint if exists fk_sender_id;
alter table project_visitation drop constraint if exists fk_project_id;
drop sequence if exists project_transfer_seq;
drop table if exists project_transfer;
