# --- !Ups
ALTER TABLE webhook ADD COLUMN git_push_only tinyint(1) default 1;
CREATE INDEX ix_webhook_git_push_only ON webhook (git_push_only);

# --- !Downs
ALTER TABLE webhook DROP COLUMN git_push_only;