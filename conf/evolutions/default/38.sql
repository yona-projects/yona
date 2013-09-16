# --- !Ups
create table pull_request_event (
  id                        bigint not null,
  pull_request_id			bigint,
  created				timestamp,
  sender_login_id			varchar(255),
  event_type				varchar(255),
  new_value					varchar(255),
  old_value					varchar(255),
  constraint pk_pull_request_event primary key (id))
;

create sequence pull_request_event_seq;

alter table pull_request_event add constraint fk_pull_request_event_1 foreign key (pull_request_id) references pull_request(id) on delete restrict on update restrict;

# --- !Downs
drop table if exists pull_request_event;

drop sequence if exists pull_request_event_seq;