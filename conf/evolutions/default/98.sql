# --- !Ups

create index ix_issue_voter_issue_id_1 on issue_voter (issue_id);
create index ix_issue_voter_user_id_2 on issue_voter (user_id);

create index ix_issue_comment_voter_issue_id_1 on issue_comment_voter (issue_comment_id);
create index ix_issue_comment_voter_user_id_2 on issue_comment_voter (user_id);

# --- !Downs

drop index if exists ix_issue_voter_issue_id_1;
drop index if exists ix_issue_voter_user_id_2;

drop index if exists ix_issue_comment_voter_issue_id_1;
drop index if exists ix_issue_comment_voter_user_id_2;
