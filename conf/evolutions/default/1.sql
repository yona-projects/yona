# --- Created by Ebean DDL
# To stop Ebean DDL generation, remove this comment and start using Evolutions

# --- !Ups

create table article (
  article_num               integer not null,
  title                     varchar(255),
  contents                  varchar(255),
  writer                    varchar(255),
  date                      timestamp,
  constraint pk_article primary key (article_num))
;

create table issue (
  id                        bigint not null,
  title                     varchar(255),
  body                      varchar(255),
  constraint pk_issue primary key (id))
;

create table project (
  id                        bigint not null,
  name                      varchar(255),
  overview                  varchar(255),
  share_option              boolean,
  vcs                       varchar(255),
  constraint pk_project primary key (id))
;

create table reply (
  reply_num                 integer not null,
  article_num               integer,
  contents                  varchar(255),
  writer                    varchar(255),
  date                      timestamp,
  constraint pk_reply primary key (reply_num))
;

create sequence article_seq;

create sequence issue_seq;

create sequence project_seq;

create sequence reply_seq;




# --- !Downs

SET REFERENTIAL_INTEGRITY FALSE;

drop table if exists article;

drop table if exists issue;

drop table if exists project;

drop table if exists reply;

SET REFERENTIAL_INTEGRITY TRUE;

drop sequence if exists article_seq;

drop sequence if exists issue_seq;

drop sequence if exists project_seq;

drop sequence if exists reply_seq;

