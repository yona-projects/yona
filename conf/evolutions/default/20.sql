# --- !Ups
ALTER TABLE attachment DROP CONSTRAINT IF EXISTS ck_attachment_container_type;
ALTER TABLE comment_thread DROP CONSTRAINT IF EXISTS ck_comment_thread_state;
ALTER TABLE comment_thread DROP CONSTRAINT IF EXISTS ck_comment_thread_start_side;
ALTER TABLE comment_thread DROP CONSTRAINT IF EXISTS ck_comment_thread_end_side;
ALTER TABLE commit_comment DROP CONSTRAINT IF EXISTS ck_commit_comment_side;
ALTER TABLE issue DROP CONSTRAINT IF EXISTS ck_issue_state;
ALTER TABLE issue_event DROP CONSTRAINT IF EXISTS ck_issue_event_event_type;
ALTER TABLE mention DROP CONSTRAINT IF EXISTS ck_mention_resource_type;
ALTER TABLE milestone DROP CONSTRAINT IF EXISTS ck_milestone_state;
ALTER TABLE notification_event DROP CONSTRAINT IF EXISTS ck_notification_event_resource_type;
ALTER TABLE notification_event DROP CONSTRAINT IF EXISTS ck_notification_event_event_type;
ALTER TABLE original_email DROP CONSTRAINT IF EXISTS ck_original_email_resource_type;
ALTER TABLE project DROP CONSTRAINT IF EXISTS ck_project_project_scope;
ALTER TABLE property DROP CONSTRAINT IF EXISTS ck_property_name;
ALTER TABLE pull_request DROP CONSTRAINT IF EXISTS ck_pull_request_state;
ALTER TABLE pull_request_commit DROP CONSTRAINT IF EXISTS ck_pull_request_commit_state;
ALTER TABLE pull_request_event DROP CONSTRAINT IF EXISTS ck_pull_request_event_event_type;
ALTER TABLE unwatch DROP CONSTRAINT IF EXISTS ck_unwatch_resource_type;
ALTER TABLE n4user DROP CONSTRAINT IF EXISTS ck_n4user_state;
ALTER TABLE user_project_notification DROP CONSTRAINT IF EXISTS ck_user_project_notification_notification_type;
ALTER TABLE watch DROP CONSTRAINT IF EXISTS ck_watch_resource_type;

# --- !Downs

