# --- !Ups

ALTER TABLE project ADD COLUMN watching_count BIGINT;
UPDATE project SET watching_count = 0 WHERE watching_count IS NULL;

create table user_watching_project (
  user_id                        bigint not null,
  project_id                bigint not null,
  constraint pk_user_watching_project primary key (user_id, project_id));

# --- !Downs

DROP TABLE IF EXISTS user_watching_project;

ALTER TABLE project DROP COLUMN watching_count;
