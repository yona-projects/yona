# --- !Ups
CREATE TABLE title_head (
  id                         BIGINT NOT NULL,
  project_id                 BIGINT,
  head_keyword               VARCHAR(255),
  frequency                  INTEGER,
  CONSTRAINT pk_title_head PRIMARY KEY (id),
  CONSTRAINT fk_title_head_project FOREIGN KEY (project_id) REFERENCES project (id) on DELETE CASCADE
);

CREATE index ix_title_head_project_id ON title_head (project_id);
CREATE index ix_title_head_head_keyword ON title_head (head_keyword);

create sequence title_head_seq;

# --- !Downs
DROP TABLE title_head;
DROP SEQUENCE title_head_seq;