# --- !Ups

UPDATE pull_request_event
SET new_value = 'DONE', old_value = 'CANCEL', event_type = 'PULL_REQUEST_REVIEW_STATE_CHANGED'
WHERE event_type = 'PULL_REQUEST_REVIEWED';

UPDATE pull_request_event
SET new_value = 'CANCEL', old_value = 'DONE', event_type = 'PULL_REQUEST_REVIEW_STATE_CHANGED'
WHERE event_type = 'PULL_REQUEST_UNREVIEWED';

UPDATE notification_event
SET new_value = 'DONE', old_value = 'CANCEL', event_type = 'PULL_REQUEST_REVIEW_STATE_CHANGED'
WHERE event_type = 'PULL_REQUEST_REVIEWED';

UPDATE notification_event
SET new_value = 'CANCEL', old_value = 'DONE', event_type = 'PULL_REQUEST_REVIEW_STATE_CHANGED'
WHERE event_type = 'PULL_REQUEST_UNREVIEWED';


# --- !Downs

UPDATE pull_request_event
SET new_value = sender_login_id, old_value = null, event_type = 'PULL_REQUEST_REVIEWED'
WHERE event_type = 'PULL_REQUEST_REVIEW_STATE_CHANGED' AND new_value = 'DONE';

UPDATE pull_request_event
SET new_value = sender_login_id, old_value = null, event_type = 'PULL_REQUEST_UNREVIEWED'
WHERE event_type = 'PULL_REQUEST_REVIEW_STATE_CHANGED' AND new_value = 'CANCEL';

UPDATE notification_event
SET new_value = (SELECT login_id FROM n4user WHERE id = sender_id), old_value = null, event_type = 'PULL_REQUEST_REVIEWED'
WHERE event_type = 'PULL_REQUEST_REVIEW_STATE_CHANGED' AND new_value = 'DONE';

UPDATE notification_event
SET new_value = (SELECT login_id FROM n4user WHERE id = sender_id), old_value = null, event_type = 'PULL_REQUEST_UNREVIEWED'
WHERE event_type = 'PULL_REQUEST_REVIEW_STATE_CHANGED' AND new_value = 'CANCEL';
