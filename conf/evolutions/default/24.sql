# --- !Ups
ALTER TABLE webhook ADD COLUMN webhook_type tinyint(1) default 1;
CREATE INDEX ix_webhook_webhook_type ON webhook (webhook_type);

# --- !Downs
ALTER TABLE webhook DROP COLUMN webhook_type;