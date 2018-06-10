# --- !Ups
CREATE TABLE favorite_issue (
  id                      BIGINT NOT NULL,
  user_id                 BIGINT,
  issue_id                BIGINT,
  CONSTRAINT pk_favorite_issue PRIMARY KEY (id),
  CONSTRAINT uq_favorite_issue_user_id_issue_id_1 UNIQUE (user_id, issue_id),
  CONSTRAINT fk_favorite_issue_user FOREIGN KEY (user_id) REFERENCES n4user (id) on DELETE CASCADE,
  CONSTRAINT fk_favorite_issue_issue FOREIGN KEY (issue_id) REFERENCES issue (id) on DELETE CASCADE
  )
;

CREATE index ix_favorite_issue_user_1 ON favorite_issue (user_id);
CREATE index ix_favorite_issue_project_2 ON favorite_issue (issue_id);

create sequence favorite_issue_seq;

# --- !Downs
DROP TABLE favorite_issue;
DROP SEQUENCE favorite_issue_seq;