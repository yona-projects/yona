# --- !Ups

CREATE TABLE simple_comment (
  id                        bigint not null,
  contents                  varchar(4000),
  created_date              timestamp,
  author_id                 bigint,
  author_login_id           varchar(255),
  author_name               varchar(255),
  resource_key              varchar(255),
  constraint pk_simple_comment primary key (id)
);

CREATE SEQUENCE simple_comment_seq;

# --- !Downs

DROP SEQUENCE IF EXISTS simple_comment_seq;

DROP TABLE IF EXISTS simple_comment;
