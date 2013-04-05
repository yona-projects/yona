# --- !Ups

DROP TABLE IF EXISTS task_board;
DROP TABLE IF EXISTS task_comment;
DROP TABLE IF EXISTS card;
DROP TABLE IF EXISTS card_assignee;
DROP TABLE IF EXISTS card_label;
DROP TABLE IF EXISTS checklist;

DROP SEQUENCE IF EXISTS task_board_seq;
DROP SEQUENCE IF EXISTS task_comment_seq;
DROP SEQUENCE IF EXISTS card_seq;
DROP SEQUENCE IF EXISTS card_assignee_seq;
DROP SEQUENCE IF EXISTS card_label_seq;
DROP SEQUENCE IF EXISTS checklist_seq;

# --- !Downs

CREATE TABLE task_board (
  id                        BIGINT NOT NULL,
  project_id                BIGINT,
  CONSTRAINT pk_task_board PRIMARY KEY (id))
;

CREATE TABLE task_comment (
  id                        BIGINT NOT NULL,
  body                      VARCHAR(255),
  card_id                   BIGINT,
  CONSTRAINT pk_task_comment PRIMARY KEY (id))
;

CREATE TABLE task_board (
  id                        BIGINT NOT NULL,
  project_id                BIGINT,
  CONSTRAINT pk_task_board PRIMARY KEY (id))
;

CREATE TABLE task_comment (
  id                        BIGINT NOT NULL,
  body                      VARCHAR(255),
  card_id                   BIGINT,
  CONSTRAINT pk_task_comment PRIMARY KEY (id))
;

CREATE TABLE checklist (
  id                        BIGINT NOT NULL,
  title                     VARCHAR(255),
  CONSTRAINT pk_checklist PRIMARY KEY (id))
;

CREATE TABLE card_assignee (
  id                        BIGINT NOT NULL,
  card_id                   BIGINT,
  project_user_id           BIGINT,
  CONSTRAINT pk_card_assignee PRIMARY KEY (id))
;

CREATE SEQUENCE IF NOT EXISTS task_board_seq;
CREATE SEQUENCE IF NOT EXISTS task_comment_seq;
CREATE SEQUENCE IF NOT EXISTS card_seq;
CREATE SEQUENCE IF NOT EXISTS card_assignee_seq;
CREATE SEQUENCE IF NOT EXISTS card_label_seq;
CREATE SEQUENCE IF NOT EXISTS checklist_seq;
