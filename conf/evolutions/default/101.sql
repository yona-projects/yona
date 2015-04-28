# --- !Ups
-- remove duplicated rows in pull_request_reviewers table
create table prr_tmp (pull_request_id bigint, user_id bigint);

insert into prr_tmp (pull_request_id, user_id) select distinct pull_request_id, user_id from pull_request_reviewers;

truncate table pull_request_reviewers;

insert into pull_request_reviewers (pull_request_id, user_id) select pull_request_id, user_id from prr_tmp;

drop table prr_tmp;

# --- !Downs
