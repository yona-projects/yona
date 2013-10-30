# --- !Ups
create table pull_request_commit (
  id                        bigint not null,
  pull_request_id			bigint,
  commit_id					varchar(255),
  commit_short_id			varchar(7),
  commit_message			varchar(2000),
  created					timestamp,
  author_date				timestamp,
  author_email				varchar(255),
  state						varchar(10),
  constraint pk_pull_request_commit primary key (id))
;

create sequence pull_request_commit_seq;

alter table pull_request_commit add constraint fk_pull_request_commit_1 foreign key (pull_request_id) references pull_request(id) on delete restrict on update restrict;

# --- !Downs
drop table if exists pull_request_commit;

drop sequence if exists pull_request_commit_seq;

