# --- !Ups
update notification_event set event_type = 'NEW_ISSUE' where event_type = '1';
update notification_event set event_type = 'NEW_POSTING' where event_type = '2';
update notification_event set event_type = 'NEW_PULL_REQUEST' where event_type = '3';
update notification_event set event_type = 'ISSUE_STATE_CHANGED' where event_type = '4';
update notification_event set event_type = 'ISSUE_ASSIGNEE_CHANGED' where event_type = '5';
update notification_event set event_type = 'PULL_REQUEST_STATE_CHANGED' where event_type = '6';
update notification_event set event_type = 'NEW_COMMENT' where event_type = '7';
update notification_event set event_type = 'NEW_PULL_REQUEST_COMMENT' where event_type = '8';
update notification_event set event_type = 'NEW_PULL_REQUEST_COMMENT' where event_type = '9';
update notification_event set event_type = 'PULL_REQUEST_MERGED' where event_type = '10';
update notification_event set event_type = 'ISSUE_REFERRED' where event_type = '11';
update notification_event set event_type = 'PULL_REQUEST_COMMIT_CHANGED' where event_type = '12';
update notification_event set event_type = 'NEW_COMMIT' where event_type = '13';
update notification_event set event_type = 'PULL_REQUEST_REVIEWED' where event_type = '14';
update notification_event set event_type = 'PULL_REQUEST_UNREVIEWED' where event_type = '15';

update pull_request_event set event_type = 'NEW_ISSUE' where event_type = '1';
update pull_request_event set event_type = 'NEW_POSTING' where event_type = '2';
update pull_request_event set event_type = 'NEW_PULL_REQUEST' where event_type = '3';
update pull_request_event set event_type = 'ISSUE_STATE_CHANGED' where event_type = '4';
update pull_request_event set event_type = 'ISSUE_ASSIGNEE_CHANGED' where event_type = '5';
update pull_request_event set event_type = 'PULL_REQUEST_STATE_CHANGED' where event_type = '6';
update pull_request_event set event_type = 'NEW_COMMENT' where event_type = '7';
update pull_request_event set event_type = 'NEW_PULL_REQUEST_COMMENT' where event_type = '8';
update pull_request_event set event_type = 'NEW_PULL_REQUEST_COMMENT' where event_type = '9';
update pull_request_event set event_type = 'PULL_REQUEST_MERGED' where event_type = '10';
update pull_request_event set event_type = 'ISSUE_REFERRED' where event_type = '11';
update pull_request_event set event_type = 'PULL_REQUEST_COMMIT_CHANGED' where event_type = '12';
update pull_request_event set event_type = 'NEW_COMMIT' where event_type = '13';
update pull_request_event set event_type = 'PULL_REQUEST_REVIEWED' where event_type = '14';
update pull_request_event set event_type = 'PULL_REQUEST_UNREVIEWED' where event_type = '15';

update issue_event set event_type = 'NEW_ISSUE' where event_type = '1';
update issue_event set event_type = 'NEW_POSTING' where event_type = '2';
update issue_event set event_type = 'NEW_PULL_REQUEST' where event_type = '3';
update issue_event set event_type = 'ISSUE_STATE_CHANGED' where event_type = '4';
update issue_event set event_type = 'ISSUE_ASSIGNEE_CHANGED' where event_type = '5';
update issue_event set event_type = 'PULL_REQUEST_STATE_CHANGED' where event_type = '6';
update issue_event set event_type = 'NEW_COMMENT' where event_type = '7';
update issue_event set event_type = 'NEW_PULL_REQUEST_COMMENT' where event_type = '8';
update issue_event set event_type = 'NEW_PULL_REQUEST_COMMENT' where event_type = '9';
update issue_event set event_type = 'PULL_REQUEST_MERGED' where event_type = '10';
update issue_event set event_type = 'ISSUE_REFERRED' where event_type = '11';
update issue_event set event_type = 'PULL_REQUEST_COMMIT_CHANGED' where event_type = '12';
update issue_event set event_type = 'NEW_COMMIT' where event_type = '13';
update issue_event set event_type = 'PULL_REQUEST_REVIEWED' where event_type = '14';
update issue_event set event_type = 'PULL_REQUEST_UNREVIEWED' where event_type = '15';

# --- !Downs
