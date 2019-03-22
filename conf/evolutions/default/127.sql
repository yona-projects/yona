# --- !Ups
ALTER TABLE issue ADD COLUMN weight tinyint(2) default 0;
CREATE INDEX ix_issue_weight ON issue (weight);

# --- !Downs
ALTER TABLE issue DROP COLUMN weight;
