# --- !Ups

-- Create category table

CREATE TABLE issue_label_category (
  id                        BIGINT NOT NULL,
  project_id                BIGINT NOT NULL,
  name                      VARCHAR(255),
  is_exclusive              BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_issue_label_category PRIMARY KEY (id))
;

CREATE SEQUENCE issue_label_category_seq;

ALTER TABLE issue_label_category ADD CONSTRAINT fk_issue_label_category_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE RESTRICT ON UPDATE RESTRICT;

-- Migration: Create categories based on the data issue_label table has.

INSERT INTO issue_label_category(id, project_id, name) SELECT nextval('issue_label_category_seq'), project_id, category FROM (SELECT DISTINCT project_id, category FROM issue_label);

-- Migration: Replace category column with new category_id column in issue_label table.

ALTER TABLE issue_label ADD COLUMN category_id BIGINT;

UPDATE issue_label A SET category_id = (SELECT issue_label_category.id FROM issue_label_category WHERE A.project_id = issue_label_category.project_id AND A.category = issue_label_category.name);

ALTER TABLE issue_label ALTER COLUMN category_id SET NOT NULL;

ALTER TABLE issue_label DROP COLUMN category;

-- Set a constraint for the relation between issue_label and issue_label_category.

ALTER TABLE issue_label ADD CONSTRAINT fk_issue_label_issue_label_category FOREIGN KEY (category_id) REFERENCES issue_label_category (id) ON DELETE RESTRICT ON UPDATE RESTRICT;

# --- !Downs

ALTER TABLE issue_label ADD COLUMN category VARCHAR(255);

UPDATE issue_label SET category=issue_label_category.name WHERE issue_label.project_id = issue_label_category.project_id AND issue_label.category_id = issue_label_category.id;

ALTER TABLE issue_label DROP COLUMN category_id;

DROP TABLE IF EXISTS issue_label_category;

DROP SEQUENCE IF EXISTS issue_label_category_seq;
