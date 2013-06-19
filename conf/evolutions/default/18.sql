# --- !Ups

CREATE TABLE ISSUE_EXPLICIT_WATCHER (
  issue_id                  bigint,
  n4user_id                 bigint,
);

CREATE TABLE ISSUE_EXPLICIT_UNWATCHER (
  issue_id                  bigint,
  n4user_id                 bigint,
);

CREATE TABLE POSTING_EXPLICIT_WATCHER (
  posting_id                bigint,
  n4user_id                 bigint,
);

CREATE TABLE POSTING_EXPLICIT_UNWATCHER (
  posting_id                bigint,
  n4user_id                 bigint,
);

# --- !Downs

DROP TABLE IF EXISTS ISSUE_EXPLICIT_WATCHER;
DROP TABLE IF EXISTS ISSUE_EXPLICIT_UNWATCHER;
DROP TABLE IF EXISTS POSTING_EXPLICIT_WATCHER;
DROP TABLE IF EXISTS POSTING_EXPLICIT_UNWATCHER;
