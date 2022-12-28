# --- !Ups
CREATE TABLE recent_issue (
    id           BIGINT AUTO_INCREMENT NOT NULL,
    user_id      BIGINT,
    issue_id     BIGINT,
    posting_id   BIGINT,
    title        VARCHAR(255),
    url          VARCHAR(255),
    created_date datetime,
    CONSTRAINT pk_recent_issue PRIMARY KEY (id),
    CONSTRAINT uq_recent_issue_user_id_issue_id_1 UNIQUE (user_id, issue_id),
    CONSTRAINT uq_recent_issue_user_id_posting_id_1 UNIQUE (user_id, posting_id),
    CONSTRAINT fk_recent_issue_user FOREIGN KEY (user_id) REFERENCES n4user (id) on DELETE CASCADE,
    CONSTRAINT fk_recent_issue_issue FOREIGN KEY (issue_id) REFERENCES issue (id) on DELETE CASCADE
);

CREATE index ix_recent_issue_user_1 ON recent_issue (user_id);
CREATE index ix_recent_issue_issue_2 ON recent_issue (user_id, issue_id);
CREATE index ix_recent_issue_posting_3 ON recent_issue (user_id, posting_id);

# --- !Downs
DROP TABLE recent_issue;
