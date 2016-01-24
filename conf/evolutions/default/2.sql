# --- !Ups
create index ix_issue_voter_user_id on issue_voter (user_id);
create index ix_issue_comment_voter_user_id on issue_comment_voter (user_id);

create index ix_original_email_resource_id on original_email (resource_id);

CREATE INDEX ix_attachment_container ON attachment (container_type, container_id);

create index ix_mention_resource_type on mention (resource_type);

# --- !Downs

drop index if exists ix_issue_voter_user_id;
drop index if exists ix_issue_comment_voter_user_id;

drop index if exists ix_original_email_resource_id;

drop index if exists ix_attachment_container;

drop index if exists ix_mention_resource_type;
