# --- !Ups

ALTER TABLE project ADD COLUMN original_project_id varchar(255);

create table pull_request (
  id                        bigint not null,
  title                     varchar(255),
  body                      clob,
  to_project_id             bigint,
  from_project_id           bigint,
  to_branch                 varchar(255),
  from_branch               varchar(255),
  contributor_id            bigint,
  receiver_id               bigint,
  created                   timestamp,
  updated                   timestamp,
  received                  timestamp,
  state                     integer,
  constraint pk_pull_request primary key (id));

create sequence pull_request_seq;

# --- !Downs

DROP SEQUENCE IF EXISTS pull_request_seq;

DROP TABLE IF EXISTS pull_request;

ALTER TABLE project DROP COLUMN original_project_id;
