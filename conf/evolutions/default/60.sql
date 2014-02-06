# --- !Ups
DELETE FROM user_project_notification WHERE notification_type = 'ISSUE_REFERRED';

# --- !Downs
