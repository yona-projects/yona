# --- !Ups

ALTER TABLE user_project_notification DROP CONSTRAINT IF EXISTS ck_user_project_notification_notification_type;
UPDATE user_project_notification SET notification_type='NEW_PULL_REQUEST_COMMENT' WHERE notification_type='NEW_SIMPLE_COMMENT';
UPDATE user_project_notification SET notification_type='NEW_COMMIT_COMMENT' WHERE notification_type='NEW_CODE_COMMENT';

# --- !Downs

ALTER TABLE user_project_notification DROP CONSTRAINT IF EXISTS ck_user_project_notification_notification_type;
UPDATE user_project_notification SET notification_type='NEW_SIMPLE_COMMENT' WHERE notification_type='NEW_PULL_REQUEST_COMMENT';
UPDATE user_project_notification SET notification_type='NEW_CODE_COMMENT' WHERE notification_type='NEW_COMMIT_COMMENT';
