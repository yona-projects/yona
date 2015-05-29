# --- !Ups

ALTER TABLE PROJECT ADD previous_name VARCHAR(255);
ALTER TABLE PROJECT ADD previous_owner_login_id VARCHAR(255);
ALTER TABLE PROJECT ADD previous_name_changed_time BIGINT;

CREATE INDEX ix_project_previous_01 ON project(previous_owner_login_id, previous_name);

# --- !Downs

ALTER TABLE project DROP COLUMN IF EXISTS previous_name;
ALTER TABLE project DROP COLUMN IF EXISTS previous_owner_login_id;
ALTER TABLE project DROP COLUMN IF EXISTS previous_name_changed_time;

DROP INDEX IF EXISTS ix_project_previous_01;
