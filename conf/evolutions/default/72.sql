# --- !Ups

-- Migrate PullRequestComment to ReviewComment and CommentThread

ALTER TABLE comment_thread ADD COLUMN tmp_pull_request_comment_id bigint;
ALTER TABLE comment_thread ADD COLUMN tmp_commit_comment_id bigint;
ALTER TABLE review_comment ADD COLUMN tmp_pull_request_comment_id bigint;
ALTER TABLE review_comment ADD COLUMN tmp_commit_comment_id bigint;

INSERT INTO comment_thread (
    id,
    author_id,
    author_login_id,
    author_name,
    commit_id,
    created_date,
    dtype,
    end_column,
    end_line,
    end_side,
    path,
    prev_commit_id,
    project_id,
    pull_request_id,
    start_column,
    start_line,
    start_side,
    state
)
SELECT
    nextval('comment_thread_seq'),
    author_id,
    author_login_id,
    author_name,
    commit_b,
    created_date,
    'ranged',
    2147483647, -- Integer max. See http://www.h2database.com/html/datatypes.html
    line,
    side,
    path,
    commit_a,
    pull_request.to_project_id,
    pull_request_id,
    1,
    line,
    side,
    'OPEN'
FROM pull_request_comment, pull_request,
    (SELECT MIN(c.id) AS id
    FROM pull_request_comment AS c,
        (SELECT commit_b, path, line, MIN(created_date) AS created_date
        FROM pull_request_comment
        WHERE line IS NOT null
        GROUP BY commit_b, path, line) AS first_comment_on_thread
    WHERE
        first_comment_on_thread.commit_b = c.commit_b AND
        first_comment_on_thread.path = c.path AND
        first_comment_on_thread.line = c.line AND
        first_comment_on_thread.created_date = c.created_date
    GROUP BY c.commit_b, c.path, c.line) AS c_ids
WHERE
    pull_request_comment.id = c_ids.id AND
    pull_request.id = pull_request_id;

INSERT INTO comment_thread (
    id,
    author_id,
    author_login_id,
    author_name,
    commit_id,
    created_date,
    dtype,
    prev_commit_id,
    project_id,
    pull_request_id,
    state,
    tmp_pull_request_comment_id
)
SELECT
    nextval('comment_thread_seq'),
    author_id,
    author_login_id,
    author_name,
    commit_b,
    created_date,
    'non_ranged',
    commit_a,
    pull_request.to_project_id,
    pull_request_id,
    'OPEN',
    pull_request_comment.id
FROM pull_request_comment, pull_request
WHERE line IS null AND pull_request.id = pull_request_id AND pull_request.to_project_id IS NOT null;

INSERT INTO review_comment (
    id,
    author_id,
    author_login_id,
    author_name,
    contents,
    created_date,
    thread_id,
    tmp_pull_request_comment_id
)
SELECT
    nextval('review_comment_seq'),
    c.author_id,
    c.author_login_id,
    c.author_name,
    c.contents,
    c.created_date,
    t.id,
    c.id
FROM pull_request_comment c, comment_thread t
WHERE c.id = t.tmp_pull_request_comment_id;

INSERT INTO review_comment (
    id,
    author_id,
    author_login_id,
    author_name,
    contents,
    created_date,
    thread_id,
    tmp_pull_request_comment_id
)
SELECT
    nextval('review_comment_seq'),
    c.author_id,
    c.author_login_id,
    c.author_name,
    c.contents,
    c.created_date,
    t.id,
    c.id
FROM
    pull_request_comment c, comment_thread t
WHERE
    c.pull_request_id = t.pull_request_id AND
    c.commit_b = t.commit_id AND
    c.commit_a = t.prev_commit_id AND
    c.path = t.path AND
    c.line = t.start_line;

UPDATE comment_thread
SET comment_thread.state = 'CLOSED'
WHERE comment_thread.id IN (
    SELECT t.id
    FROM comment_thread t, pull_request pr
    WHERE t.pull_request_id = pr.id AND pr.state IN (2, 3, 6));

DROP TABLE IF EXISTS pull_request_comment;

DROP SEQUENCE IF EXISTS pull_request_comment_seq;

-- Migrate CommitComment to ReviewComment and CommentThread

INSERT INTO comment_thread (
    id,
    author_id,
    author_login_id,
    author_name,
    commit_id,
    created_date,
    dtype,
    end_column,
    end_line,
    end_side,
    path,
    project_id,
    pull_request_id,
    start_column,
    start_line,
    start_side,
    state
)
SELECT
    nextval('comment_thread_seq'),
    author_id,
    author_login_id,
    author_name,
    commit_id,
    created_date,
    'ranged',
    2147483647, -- Integer max. See http://www.h2database.com/html/datatypes.html
    line,
    side,
    path,
    project_id,
    null,
    1,
    line,
    side,
    'OPEN'
FROM commit_comment,
    (SELECT MIN(cc.id) AS id
    FROM commit_comment AS cc,
        (SELECT c.commit_id, c.path, c.line, MIN(c.created_date) AS created_date
        FROM commit_comment c, project p
        WHERE
            c.line IS NOT null AND
            c.project_id = p.id AND
            p.vcs = 'GIT'
        GROUP BY c.commit_id, c.path, c.line) AS first_comment_on_thread
    WHERE
        first_comment_on_thread.commit_id = cc.commit_id AND
        first_comment_on_thread.path = cc.path AND
        first_comment_on_thread.line = cc.line AND
        first_comment_on_thread.created_date = cc.created_date
    GROUP BY cc.commit_id, cc.path, cc.line) AS c_ids
WHERE
    commit_comment.id = c_ids.id;

INSERT INTO comment_thread (
    id,
    author_id,
    author_login_id,
    author_name,
    commit_id,
    created_date,
    dtype,
    project_id,
    pull_request_id,
    state,
    tmp_commit_comment_id
)
SELECT
    nextval('comment_thread_seq'),
    c.author_id,
    c.author_login_id,
    c.author_name,
    c.commit_id,
    c.created_date,
    'non_ranged',
    c.project_id,
    null,
    'OPEN',
    c.id
FROM commit_comment c, project p
WHERE c.project_id = p.id AND p.vcs = 'GIT' AND c.line IS null;

INSERT INTO review_comment (
    id,
    author_id,
    author_login_id,
    author_name,
    contents,
    created_date,
    thread_id,
    tmp_commit_comment_id
)
SELECT
    nextval('review_comment_seq'),
    c.author_id,
    c.author_login_id,
    c.author_name,
    c.contents,
    c.created_date,
    t.id,
    c.id
FROM commit_comment c, comment_thread t
WHERE t.pull_request_id IS null AND c.commit_id = t.commit_id AND ((c.path = t.path AND c.line = t.start_line) OR tmp_commit_comment_id = c.id);

DELETE FROM commit_comment WHERE id IN (
    SELECT c.id FROM commit_comment c, project p WHERE c.project_id = p.id AND p.vcs = 'GIT'
);

ALTER TABLE watch DROP CONSTRAINT IF EXISTS ck_watch_resource_type;
UPDATE watch SET resource_type='REVIEW_COMMENT' WHERE resource_type='PULL_REQUEST_COMMENT';
ALTER TABLE watch ADD CONSTRAINT ck_watch_resource_type check (resource_type in ('ISSUE_POST','ISSUE_ASSIGNEE','ISSUE_STATE','ISSUE_CATEGORY','ISSUE_MILESTONE','ISSUE_LABEL','BOARD_POST','BOARD_CATEGORY','BOARD_NOTICE','CODE','MILESTONE','WIKI_PAGE','PROJECT_SETTING','SITE_SETTING','USER','USER_AVATAR','PROJECT','ATTACHMENT','ISSUE_COMMENT','NONISSUE_COMMENT','LABEL','PROJECT_LABELS','FORK','COMMIT_COMMENT','PULL_REQUEST','REVIEW_COMMENT', 'COMMIT'));

ALTER TABLE unwatch DROP CONSTRAINT IF EXISTS ck_unwatch_resource_type;
UPDATE unwatch SET resource_type='REVIEW_COMMENT' WHERE resource_type='PULL_REQUEST_COMMENT';
ALTER TABLE unwatch ADD CONSTRAINT ck_unwatch_resource_type check (resource_type in ('ISSUE_POST','ISSUE_ASSIGNEE','ISSUE_STATE','ISSUE_CATEGORY','ISSUE_MILESTONE','ISSUE_LABEL','BOARD_POST','BOARD_CATEGORY','BOARD_NOTICE','CODE','MILESTONE','WIKI_PAGE','PROJECT_SETTING','SITE_SETTING','USER','USER_AVATAR','PROJECT','ATTACHMENT','ISSUE_COMMENT','NONISSUE_COMMENT','LABEL','PROJECT_LABELS','FORK','COMMIT_COMMENT','PULL_REQUEST','REVIEW_COMMENT', 'COMMIT'));

ALTER TABLE attachment DROP CONSTRAINT IF EXISTS ck_attachment_container_type;
UPDATE attachment
SET container_type='REVIEW_COMMENT', container_id=(SELECT review_comment.id FROM review_comment WHERE attachment.container_id=review_comment.tmp_pull_request_comment_id)
WHERE id IN (
    SELECT a.id
    FROM attachment a, review_comment c
    WHERE a.container_type='PULL_REQUEST_COMMENT' AND a.container_id=c.tmp_pull_request_comment_id);

UPDATE attachment
SET container_type='REVIEW_COMMENT', container_id=(SELECT review_comment.id FROM review_comment WHERE attachment.container_id=review_comment.tmp_commit_comment_id)
WHERE id IN (
    SELECT a.id
    FROM attachment a, review_comment c
    WHERE a.container_type='COMMIT_COMMENT' AND a.container_id=c.tmp_commit_comment_id);
UPDATE attachment SET container_type='REVIEW_COMMENT' WHERE container_type='PULL_REQUEST_COMMENT';
ALTER TABLE attachment ADD CONSTRAINT ck_attachment_container_type check (container_type in ('ISSUE_POST','ISSUE_ASSIGNEE','ISSUE_STATE','ISSUE_CATEGORY','ISSUE_MILESTONE','ISSUE_LABEL','BOARD_POST','BOARD_CATEGORY','BOARD_NOTICE','CODE','MILESTONE','WIKI_PAGE','PROJECT_SETTING','SITE_SETTING','USER','USER_AVATAR','PROJECT','ATTACHMENT','ISSUE_COMMENT','NONISSUE_COMMENT','COMMIT_COMMENT', 'REVIEW_COMMENT', 'PULL_REQUEST'));

ALTER TABLE notification_event DROP CONSTRAINT IF EXISTS ck_notification_event_resource_type;
UPDATE notification_event SET resource_type='REVIEW_COMMENT' WHERE resource_type='PULL_REQUEST_COMMENT';
UPDATE notification_event SET event_type='NEW_REVIEW_COMMENT' WHERE event_type='NEW_PULL_REQUEST_COMMENT';
ALTER TABLE notification_event ADD constraint ck_notification_event_resource_type check (resource_type in ('ISSUE_POST','ISSUE_ASSIGNEE','ISSUE_STATE','ISSUE_CATEGORY','ISSUE_MILESTONE','ISSUE_LABEL','BOARD_POST','BOARD_CATEGORY','BOARD_NOTICE','CODE','MILESTONE','WIKI_PAGE','PROJECT_SETTING','SITE_SETTING','USER','USER_AVATAR','PROJECT','ATTACHMENT','ISSUE_COMMENT','NONISSUE_COMMENT','LABEL','PROJECT_LABELS','FORK','COMMIT_COMMENT','PULL_REQUEST','REVIEW_COMMENT', 'COMMENT_THREAD'));

UPDATE user_project_notification SET notification_type='NEW_REVIEW_COMMENT' WHERE notification_type='NEW_PULL_REQUEST_COMMENT';

ALTER TABLE comment_thread DROP COLUMN IF EXISTS tmp_pull_request_comment_id;
ALTER TABLE comment_thread DROP COLUMN IF EXISTS tmp_commit_comment_id;
ALTER TABLE review_comment DROP COLUMN IF EXISTS tmp_pull_request_comment_id;
ALTER TABLE review_comment DROP COLUMN IF EXISTS tmp_commit_comment_id;

# --- !Downs

CREATE TABLE IF NOT EXISTS pull_request_comment (
    id                        bigint not null,
    contents                  varchar(4000),
    created_date              timestamp,
    author_id                 bigint,
    author_login_id           varchar(255),
    author_name               varchar(255),
    resource_key              varchar(255),
    project_id                bigint,
    commit_a                  varchar(40),
    commit_b                  varchar(40),
    commit_id                 varchar(40),
    path                      varchar(255),
    line                      integer,
    side                      varchar(16),
    pull_request_id           bigint,
    constraint pk_simple_comment primary key (id));

CREATE SEQUENCE pull_request_comment_seq;

INSERT INTO pull_request_comment (
    id,
    author_id,
    author_login_id,
    author_name,
    commit_a,
    commit_b,
    contents,
    created_date,
    line,
    path,
    pull_request_id,
    side,
)
SELECT
    nextval('pull_request_comment_seq'),
    c.author_id,
    c.author_login_id,
    c.author_name,
    t.prev_commit_id,
    t.commit_id,
    SUBSTRING(c.contents, 1, 4000),
    t.created_date,
    t.start_line,
    t.path,
    t.pull_request_id,
    t.start_side
FROM comment_thread t, review_comment c
WHERE c.thread_id = t.id AND t.pull_request_id IS NOT null;

INSERT INTO commit_comment (
    id,
    author_id,
    author_login_id,
    author_name,
    commit_id,
    contents,
    created_date,
    line,
    path,
    project_id,
    side
)
SELECT
    nextval('commit_comment_seq'),
    c.author_id,
    c.author_login_id,
    c.author_name,
    t.commit_id,
    SUBSTRING(c.contents, 1, 4000),
    c.created_date,
    t.start_line,
    t.path,
    t.project_id,
    t.start_side
FROM comment_thread t, review_comment c
WHERE c.thread_id = t.id and t.pull_request_id IS null;

DELETE FROM review_comment;
DELETE FROM comment_thread;

-- watch

ALTER TABLE watch DROP CONSTRAINT IF EXISTS ck_watch_resource_type;

UPDATE
    watch
SET
    watch.resource_type = 'PULL_REQUEST_COMMENT'
WHERE
    watch.resource_type = 'REVIEW_COMMENT' AND
    watch.id IN (SELECT watch.id
        FROM
            watch, comment_thread t, review_comment c
        WHERE
            watch.resource_id = CAST(c.id AS varchar(255)) AND
            c.thread_id = t.id AND
            t.pull_request_id IS NOT null);

UPDATE
    watch
SET
    watch.resource_type = 'COMMIT_COMMENT'
WHERE
    watch.resource_type = 'REVIEW_COMMENT';

ALTER TABLE watch ADD CONSTRAINT ck_watch_resource_type check (resource_type in ('ISSUE_POST','ISSUE_ASSIGNEE','ISSUE_STATE','ISSUE_CATEGORY','ISSUE_MILESTONE','ISSUE_LABEL','BOARD_POST','BOARD_CATEGORY','BOARD_NOTICE','CODE','MILESTONE','WIKI_PAGE','PROJECT_SETTING','SITE_SETTING','USER','USER_AVATAR','PROJECT','ATTACHMENT','ISSUE_COMMENT','NONISSUE_COMMENT','LABEL','PROJECT_LABELS','FORK','COMMIT_COMMENT','PULL_REQUEST','PULL_REQUEST_COMMENT', 'COMMIT'));

-- unwatch

ALTER TABLE unwatch DROP CONSTRAINT IF EXISTS ck_unwatch_resource_type;

UPDATE
    unwatch
SET
    unwatch.resource_type = 'PULL_REQUEST_COMMENT'
WHERE
    unwatch.resource_type = 'REVIEW_COMMENT' AND
    unwatch.id IN (SELECT unwatch.id
        FROM
            unwatch, comment_thread t, review_comment c
        WHERE
            unwatch.resource_id = CAST(c.id AS varchar(255)) AND
            c.thread_id = t.id AND
            t.pull_request_id IS NOT null);

UPDATE
    unwatch
SET
    unwatch.resource_type = 'COMMIT_COMMENT'
WHERE
    unwatch.resource_type = 'REVIEW_COMMENT';

ALTER TABLE unwatch ADD CONSTRAINT ck_unwatch_resource_type check (resource_type in ('ISSUE_POST','ISSUE_ASSIGNEE','ISSUE_STATE','ISSUE_CATEGORY','ISSUE_MILESTONE','ISSUE_LABEL','BOARD_POST','BOARD_CATEGORY','BOARD_NOTICE','CODE','MILESTONE','WIKI_PAGE','PROJECT_SETTING','SITE_SETTING','USER','USER_AVATAR','PROJECT','ATTACHMENT','ISSUE_COMMENT','NONISSUE_COMMENT','LABEL','PROJECT_LABELS','FORK','COMMIT_COMMENT','PULL_REQUEST','PULL_REQUEST_COMMENT', 'COMMIT'));

-- attachment

ALTER TABLE attachment DROP CONSTRAINT IF EXISTS ck_attachment_container_type;

UPDATE
    attachment
SET
    attachment.container_type = 'PULL_REQUEST_COMMENT'
WHERE
    attachment.container_type = 'REVIEW_COMMENT' AND
    attachment.id IN (SELECT attachment.id
        FROM
            attachment, comment_thread t, review_comment c
        WHERE
            attachment.container_id = CAST(c.id AS varchar(255)) AND
            c.thread_id = t.id AND
            t.pull_request_id IS NOT null);

UPDATE
    attachment
SET
    attachment.container_type = 'COMMIT_COMMENT'
WHERE
    attachment.container_type = 'REVIEW_COMMENT';

ALTER TABLE attachment ADD CONSTRAINT ck_attachment_container_type check (container_type in ('ISSUE_POST','ISSUE_ASSIGNEE','ISSUE_STATE','ISSUE_CATEGORY','ISSUE_MILESTONE','ISSUE_LABEL','BOARD_POST','BOARD_CATEGORY','BOARD_NOTICE','CODE','MILESTONE','WIKI_PAGE','PROJECT_SETTING','SITE_SETTING','USER','USER_AVATAR','PROJECT','ATTACHMENT','ISSUE_COMMENT','NONISSUE_COMMENT','COMMIT_COMMENT', 'PULL_REQUEST_COMMENT', 'PULL_REQUEST'));

-- notification_event

ALTER TABLE notification_event DROP CONSTRAINT IF EXISTS ck_notification_event_resource_type;

UPDATE
    notification_event
SET
    notification_event.resource_type = 'PULL_REQUEST_COMMENT',
    notification_event.event_type = 'NEW_PULL_REQUEST_COMMENT'
WHERE
    notification_event.resource_type = 'REVIEW_COMMENT' AND
    notification_event.event_type = 'NEW_REVIEW_COMMENT';

UPDATE
    notification_event
SET
    notification_event.resource_type = 'COMMIT_COMMENT'
WHERE
    notification_event.resource_type = 'REVIEW_COMMENT' AND
    notification_event.event_type = 'NEW_COMMENT';

DELETE FROM notification_event_n4user
WHERE notification_event_id IN (
    SELECT id
    FROM notification_event
    WHERE event_type = 'REVIEW_THREAD_STATE_CHANGED');

DELETE FROM
    notification_event
WHERE
    notification_event.event_type = 'REVIEW_THREAD_STATE_CHANGED';

ALTER TABLE notification_event ADD constraint ck_notification_event_resource_type check (resource_type in ('ISSUE_POST','ISSUE_ASSIGNEE','ISSUE_STATE','ISSUE_CATEGORY','ISSUE_MILESTONE','ISSUE_LABEL','BOARD_POST','BOARD_CATEGORY','BOARD_NOTICE','CODE','MILESTONE','WIKI_PAGE','PROJECT_SETTING','SITE_SETTING','USER','USER_AVATAR','PROJECT','ATTACHMENT','ISSUE_COMMENT','NONISSUE_COMMENT','LABEL','PROJECT_LABELS','FORK','COMMIT_COMMENT','PULL_REQUEST','PULL_REQUEST_COMMENT'));
