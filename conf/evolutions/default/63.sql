# --- !Ups
create table issue_voter (
    issue_id BIGINT NOT NULL,
    user_id INT NOT NULL
);

alter table issue_voter add constraint fk_issue_voter_1 foreign key (issue_id) references issue (id) on delete restrict on update restrict;
alter table issue_voter add constraint fk_issue_voter_2 foreign key (user_id) references n4user (id) on delete restrict on update restrict;
# --- !Downs
alter table issue_voter drop constraint if exists fk_issue_voter_1;
alter table issue_voter drop constraint if exists fk_issue_voter_2;
drop table if exists issue_voter;

