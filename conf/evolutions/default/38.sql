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

ALTER TABLE pull_request ADD COLUMN is_conflict boolean;
ALTER TABLE pull_request ADD COLUMN patch clob;
ALTER TABLE pull_request ADD COLUMN is_merging boolean;
ALTER TABLE pull_request ADD COLUMN conflict_files varchar(255);
UPDATE pull_request SET is_merging = false;
UPDATE pull_request SET is_conflict = false;

# --- !Downs
drop table if exists pull_request_event;

drop sequence if exists pull_request_event_seq;

ALTER TABLE pull_request DROP COLUMN is_conflict;
ALTER TABLE pull_request DROP COLUMN patch;
ALTER TABLE pull_request DROP COLUMN is_merging;
ALTER TABLE pull_request DROP COLUMN conflict_files;

