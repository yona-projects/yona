# --- !Ups
create table posting_issue_label (
  posting_id                     bigint not null,
  issue_label_id                 bigint not null,
  constraint pk_posting_issue_label primary key (posting_id, issue_label_id))
;

alter table posting_issue_label add constraint fk_posting_issue_label_issue_01 foreign key (posting_id) references posting (id) on delete restrict on update restrict;

alter table posting_issue_label add constraint fk_posting_issue_label_issue_la_02 foreign key (issue_label_id) references issue_label (id) on delete restrict on update restrict;

# --- !Downs
drop table if exists POSTING_ISSUE_LABEL;

