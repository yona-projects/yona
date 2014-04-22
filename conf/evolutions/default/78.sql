# --- !Ups
create table user_enrolled_organization (
  user_id                   bigint not null,
  organization_id           bigint not null,
  constraint pk_user_enrolled_organization primary key (user_id, organization_id));

ALTER TABLE notification_event DROP CONSTRAINT IF EXISTS ck_notification_event_resource_type;
ALTER TABLE notification_event ADD constraint ck_notification_event_resource_type check (resource_type in ('ISSUE_POST','ISSUE_ASSIGNEE','ISSUE_STATE','ISSUE_CATEGORY','ISSUE_MILESTONE','ISSUE_LABEL','BOARD_POST','BOARD_CATEGORY','BOARD_NOTICE','CODE','MILESTONE','WIKI_PAGE','PROJECT_SETTING','SITE_SETTING','USER','USER_AVATAR','PROJECT','ATTACHMENT','ISSUE_COMMENT','NONISSUE_COMMENT','LABEL','PROJECT_LABELS','FORK','COMMIT_COMMENT','PULL_REQUEST','REVIEW_COMMENT', 'COMMENT_THREAD', 'ORGANIZATION'));

# --- !Downs

drop table if exists user_enrolled_organization;

DELETE FROM notification_event_n4user WHERE notification_event_id IN (SELECT id FROM notification_event WHERE resource_type='ORGANIZATION');
DELETE FROM notification_mail WHERE notification_event_id IN (SELECT id FROM notification_event);
DELETE FROM notification_event WHERE resource_type='ORGANIZATION';
ALTER TABLE notification_event DROP CONSTRAINT IF EXISTS ck_notification_event_resource_type;
ALTER TABLE notification_event ADD constraint ck_notification_event_resource_type check (resource_type in ('ISSUE_POST','ISSUE_ASSIGNEE','ISSUE_STATE','ISSUE_CATEGORY','ISSUE_MILESTONE','ISSUE_LABEL','BOARD_POST','BOARD_CATEGORY','BOARD_NOTICE','CODE','MILESTONE','WIKI_PAGE','PROJECT_SETTING','SITE_SETTING','USER','USER_AVATAR','PROJECT','ATTACHMENT','ISSUE_COMMENT','NONISSUE_COMMENT','LABEL','PROJECT_LABELS','FORK','COMMIT_COMMENT','PULL_REQUEST','REVIEW_COMMENT', 'COMMENT_THREAD'));
