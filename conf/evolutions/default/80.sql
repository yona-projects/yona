# --- !Ups

UPDATE user_project_notification
SET notification_type = 'PULL_REQUEST_REVIEW_STATE_CHANGED', allowed = 'TRUE'
WHERE notification_type IN ('PULL_REQUEST_REVIEWED', 'PULL_REQUEST_UNREVIEWED');

DELETE
FROM user_project_notification
WHERE id NOT IN
(
    SELECT MIN(id)
    FROM user_project_notification
    GROUP BY user_id, project_id, notification_type
);


# --- !Downs

INSERT INTO user_project_notification(id, user_id, project_id, notification_type, allowed)
SELECT nextval('user_project_notification_seq'), user_id, project_id, 'PULL_REQUEST_REVIEWED', 'TRUE'
FROM (SELECT user_id, project_id
      FROM user_project_notification
      WHERE notification_type = 'PULL_REQUEST_REVIEW_STATE_CHANGED');

INSERT INTO user_project_notification(id, user_id, project_id, notification_type, allowed)
SELECT nextval('user_project_notification_seq'), user_id, project_id, 'PULL_REQUEST_UNREVIEWED', 'TRUE'
FROM (SELECT user_id, project_id
      FROM user_project_notification
      WHERE notification_type = 'PULL_REQUEST_REVIEW_STATE_CHANGED');

DELETE FROM user_project_notification
WHERE notification_type = 'PULL_REQUEST_REVIEW_STATE_CHANGED';
