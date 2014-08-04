# --- !Ups

CREATE TABLE issue_comment_voter (
  issue_comment_id               BIGINT NOT NULL,
  user_id                        BIGINT NOT NULL,
  CONSTRAINT pk_issue_comment_voter PRIMARY KEY (issue_comment_id, user_id))
;

ALTER TABLE issue_comment_voter ADD CONSTRAINT fk_issue_comment_voter_issue FOREIGN KEY (issue_comment_id) REFERENCES issue_comment (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE issue_comment_voter ADD CONSTRAINT fk_issue_comment_voter_n4user FOREIGN KEY (user_id) REFERENCES n4user (id) ON DELETE RESTRICT ON UPDATE RESTRICT;


# --- !Downs

DROP TABLE IF EXISTS issue_comment_voter;
