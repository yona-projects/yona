# --- !Ups

DELETE FROM commit_comment WHERE side<>'context' and side<>'add' and side<>'remove';
UPDATE commit_comment SET side='A' WHERE side='remove';
UPDATE commit_comment SET side='B' WHERE side='context' or side='add';
ALTER TABLE attachment DROP CONSTRAINT IF EXISTS ck_commit_comment_side;
ALTER TABLE commit_comment ADD constraint ck_commit_comment_side check (side in ('A', 'B'));

DELETE FROM pull_request_comment WHERE side<>'context' and side<>'add' and side<>'remove';
UPDATE pull_request_comment SET side='A' WHERE side='remove';
UPDATE pull_request_comment SET side='B' WHERE side='context' or side='add';
ALTER TABLE attachment DROP CONSTRAINT IF EXISTS ck_pull_request_comment_side;
ALTER TABLE pull_request_comment ADD constraint ck_pull_request_comment_side check (side in ('A', 'B'));

# --- !Downs

UPDATE commit_comment SET side='remove' WHERE side='A';
UPDATE commit_comment SET side='add' WHERE side='B'; -- This possibly cause loss of a few comments' location.
ALTER TABLE attachment DROP CONSTRAINT IF EXISTS ck_commit_comment_side;

UPDATE pull_request_comment SET side='remove' WHERE side='A';
UPDATE pull_request_comment SET side='add' WHERE side='B'; -- This possibly cause loss of a few comments' location.
ALTER TABLE attachment DROP CONSTRAINT IF EXISTS ck_pull_request_comment_side;
