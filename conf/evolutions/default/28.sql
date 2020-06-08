# --- !Ups
UPDATE webhook SET webhook_type = 3 WHERE git_push_only = 1;
ALTER TABLE webhook CHANGE git_push_only git_push tinyint(1);

# --- !Downs
ALTER TABLE webhook CHANGE git_push git_push_only tinyint(1);
UPDATE webhook SET git_push_only = 1 WHERE webhook_type = 3;
UPDATE webhook SET webhook_type = 0 WHERE webhook_type = 3;

