# --- !Ups
create table email (
  id                        bigint not null,
  user_id                   bigint,
  email                     varchar(255),
  token                     varchar(255),
  valid                     boolean,
  constraint pk_email primary key (id))
;

create sequence email_seq;

alter table email add constraint fk_email_user foreign key (user_id) references n4user (id) on delete restrict on update restrict;

# --- !Downs
drop sequence if exists email_seq;

drop table if exists email;
