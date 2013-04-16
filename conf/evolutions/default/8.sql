# --- !Ups

CREATE TABLE project_tag (
  project_id                     BIGINT NOT NULL,
  tag_id                         BIGINT NOT NULL,
  CONSTRAINT pk_project_tag PRIMARY KEY (project_id, tag_id));

CREATE TABLE tag (
  id                       BIGINT NOT NULL,
  name                      VARCHAR(255),
  CONSTRAINT uq_tag_name UNIQUE (NAME),
  CONSTRAINT pk_tag PRIMARY KEY (ID));

CREATE SEQUENCE tag_seq;

# --- !Downs

DROP SEQUENCE IF EXISTS tag_seq;
DROP TABLE IF EXISTS project_tag;
DROP TABLE IF EXISTS tag;
