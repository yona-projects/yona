# --- !Ups

create table issue_voter_temp (
  issue_id bigint not null,
  user_id  bigint not null
);

insert into issue_voter_temp select * from issue_voter group by issue_id, user_id;
drop table issue_voter;
alter table issue_voter_temp rename to issue_voter;

alter table issue_voter add constraint pk_issue_voter primary key (issue_id, user_id);
alter table issue_voter add constraint fk_issue_voter_issue_1 foreign key (issue_id) references issue (id) on delete restrict on update restrict;
alter table issue_voter add constraint fk_issue_voter_n4user_2 foreign key (user_id) references n4user (id) on delete restrict on update restrict;

# --- !Downs

alter table issue_voter drop constraint if exists pk_issue_voter;
