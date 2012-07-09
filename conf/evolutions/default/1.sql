# --- Created by Ebean DDL
# To stop Ebean DDL generation, remove this comment and start using Evolutions

# --- !Ups

create table issue (
  id                        bigint not null,
  title                     varchar(255),
  body                      varchar(255),
  constraint pk_issue primary key (id))
;

create sequence issue_seq;




# --- !Downs

SET REFERENTIAL_INTEGRITY FALSE;

drop table if exists issue;

SET REFERENTIAL_INTEGRITY TRUE;

drop sequence if exists issue_seq;

