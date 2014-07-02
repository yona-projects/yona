# --- !Ups

CREATE TABLE project_menu_setting (
  id                        BIGINT NOT NULL,
  project_id                BIGINT,
  code                      BOOLEAN,
  issue                     BOOLEAN,
  pull_request              BOOLEAN,
  review                    BOOLEAN,
  milestone                 BOOLEAN,
  board                     BOOLEAN,
  CONSTRAINT pk_project_menu_setting PRIMARY KEY (id))
;

CREATE SEQUENCE project_menu_setting_seq;
ALTER TABLE project_menu_setting ADD CONSTRAINT fk_project_menu_setting FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
CREATE INDEX ix_project_menu_setting ON project_menu_setting (project_id);

INSERT INTO project_menu_setting(id, project_id, code, issue, pull_request, review, milestone, board)
SELECT nextval('project_menu_setting_seq'), id, 'TRUE', 'TRUE', 'TRUE', 'TRUE', 'TRUE', 'TRUE'
FROM (SELECT id
      FROM project);


# --- !Downs

DROP TABLE IF EXISTS project_menu_setting;
DROP SEQUENCE IF EXISTS project_menu_setting_seq;
