# --- !Ups
ALTER TABLE attachment ADD COLUMN owner_login_id VARCHAR(255);
CREATE INDEX ix_attachment_owner_login_id ON attachment (owner_login_id);
CREATE INDEX ix_attachment_created_date ON attachment (created_date);

ALTER TABLE attachment MODIFY container_id BIGINT NOT NULL;

UPDATE attachment a
SET owner_login_id = (SELECT author_login_id FROM posting b WHERE b.id = a.container_id)
WHERE container_type = 'BOARD_POST';

UPDATE attachment a
SET owner_login_id = (SELECT author_login_id FROM issue b WHERE b.id = a.container_id)
WHERE container_type = 'ISSUE_POST';

UPDATE attachment a
SET owner_login_id = (SELECT author_login_id FROM issue_comment b WHERE b.id = a.container_id)
WHERE container_type = 'ISSUE_COMMENT';

UPDATE attachment a
SET owner_login_id = (SELECT author_login_id FROM posting_comment b WHERE b.id = a.container_id)
WHERE container_type = 'NONISSUE_COMMENT';

UPDATE attachment a
SET owner_login_id = (SELECT login_id FROM n4user b WHERE a.id = b.id)
WHERE container_type in ('USER', 'USER_AVATAR');


# --- !Downs
DROP INDEX ix_attachment_owner_login_id ON attachment;
DROP INDEX ix_attachment_created_date ON attachment;
ALTER TABLE attachment DROP COLUMN owner_login_id;

