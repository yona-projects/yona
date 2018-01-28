# --- !Ups
CREATE TABLE issue_sharer (
  id                        BIGINT NOT NULL,
  created                   DATE,
  login_id                  VARCHAR(255),
  user_id                   BIGINT,
  issue_id                   BIGINT,
  CONSTRAINT pk_issue_sharer PRIMARY KEY (id),
  CONSTRAINT fk_issue_sharer_user FOREIGN KEY (user_id) REFERENCES n4user (id) on DELETE CASCADE,
  CONSTRAINT fk_issue_sharer_issue FOREIGN KEY (issue_id) REFERENCES issue (id) on DELETE CASCADE
);

CREATE index ix_issue_sharer_login_id ON issue_sharer (login_id);
CREATE index ix_issue_sharer_user_id ON issue_sharer (user_id);
CREATE index ix_issue_sharer_issue_id ON issue_sharer (issue_id);

create sequence issue_sharer_seq;

# --- !Downs
DROP TABLE issue_sharer;
DROP SEQUENCE issue_sharer_seq;
