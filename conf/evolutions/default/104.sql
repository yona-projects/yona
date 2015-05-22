# --- !Ups

create table webhook (
  id                        bigint not null,
  project_id                bigint not null,
  payload_url               varchar(4000) not null,
  secret                    varchar(256),
  constraint pk_webhook primary key (id))
;

create sequence webhook_seq;
alter table webhook add constraint fk_project_webhook foreign key (project_id) references project (id) on delete restrict on update restrict;

# --- !Downs

drop table if exists webhook;

drop sequence if exists webhook_seq;
