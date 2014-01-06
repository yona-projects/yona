# --- !Ups
create table pull_request_reviewers (
    pull_request_id BIGINT NOT NULL,
    user_id INT NOT NULL
);
alter table pull_request_reviewers add constraint fk_pull_request_reviewers_1 foreign key (pull_request_id) references pull_request (id) on delete restrict on update restrict;
alter table pull_request_reviewers add constraint fk_pull_request_reviewers_2 foreign key (user_id) references n4user (id) on delete restrict on update restrict;
alter table project add column default_review_point smallint;
update project set default_review_point = 1 where default_review_point is null;
# --- !Downs
alter table project drop column if exists default_review_point;
alter table pull_request_reviewers drop constraint if exists fk_pull_request_reviewers_1;
alter table pull_request_reviewers drop constraint if exists fk_pull_request_reviewers_2;
drop table if exists pull_request_reviewers;
