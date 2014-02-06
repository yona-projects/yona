# --- !Ups

create table comment_thread (
      dtype                     varchar(10) not null,
      id                        bigint not null,
      project_id                bigint not null,
      author_id                 bigint,
      author_login_id           varchar(255),
      author_name               varchar(255),
      state                     varchar(6),
      commit_id                 varchar(255),
      path                      varchar(255),
      start_side                varchar(1),
      start_line                integer,
      start_column              integer,
      end_side                  varchar(1),
      end_line                  integer,
      end_column                integer,
      pull_request_id           bigint,
      constraint ck_comment_thread_state check (state in ('OPEN','CLOSED')),
      constraint ck_comment_thread_start_side check (start_side in ('A','B')),
      constraint ck_comment_thread_end_side check (end_side in ('A','B')),
      constraint pk_comment_thread primary key (id))
;

create table review_comment (
      id                        bigint not null,
      contents                  clob,
      created_date              timestamp,
      author_id                 bigint,
      author_login_id           varchar(255),
      author_name               varchar(255),
      thread_id                 bigint,
      constraint pk_review_comment primary key (id))
;

create table comment_thread_n4user (
      comment_thread_id              bigint not null,
      n4user_id                      bigint not null,
      constraint pk_comment_thread_n4user primary key (comment_thread_id, n4user_id))
;

create sequence comment_thread_seq;

create sequence review_comment_seq;

alter table review_comment add constraint fk_review_comment_thread_28 foreign key (thread_id) references comment_thread (id) on delete restrict on update restrict;
create index ix_review_comment_thread_28 on review_comment (thread_id);

alter table comment_thread add constraint fk_comment_thread_project_29 foreign key (project_id) references project (id) on delete restrict on update restrict;
create index ix_comment_thread_29 on comment_thread (project_id);

alter table comment_thread_n4user add constraint fk_comment_thread_n4user_comm_01 foreign key (comment_thread_id) references comment_thread (id) on delete restrict on update restrict;

alter table comment_thread_n4user add constraint fk_comment_thread_n4user_n4us_02 foreign key (n4user_id) references n4user (id) on delete restrict on update restrict;

alter table comment_thread add constraint fk_comment_thread_pull_request_01 foreign key (pull_request_id) references pull_request (id) on delete restrict on update restrict;

# --- !Downs

drop table if exists comment_thread;

drop table if exists review_comment;

drop table if exists comment_thread_n4user;

drop sequence if exists comment_thread_seq;

drop sequence if exists review_comment_seq;
