# --- !Ups

alter table pull_request_reviewers add constraint uq_pull_request_reviewer unique (pull_request_id, user_id);

# --- !Downs

alter table pull_request_reviewers drop constraint if exists uq_pull_request_reviewer;
