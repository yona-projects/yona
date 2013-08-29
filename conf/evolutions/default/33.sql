# --- !Ups
create table issue_event (
  id                        bigint not null,
  created                   timestamp,
  sender_login_id           varchar(255),
  issue_id                  bigint,
  event_type         varchar(26),
  old_value                 varchar(255),
  new_value                 varchar(255),
  constraint ck_issue_event_event_type check (event_type in ('NEW_ISSUE','NEW_POSTING','ISSUE_ASSIGNEE_CHANGED','ISSUE_STATE_CHANGED','NEW_COMMENT','NEW_PULL_REQUEST','NEW_SIMPLE_COMMENT','PULL_REQUEST_STATE_CHANGED')),
  constraint pk_issue_event primary key (id))
;

create sequence issue_event_seq;

alter table issue_event add constraint fk_issue_event_issue_8 foreign key (issue_id) references issue (id) on delete restrict on update restrict;

create index ix_issue_event_issue_8 on issue_event (issue_id);

# --- !Downs
drop table if exists issue_event;

drop sequence if exists issue_event_seq;
