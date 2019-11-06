# --- !Ups
CREATE TABLE webhook_thread (
  id                        BIGINT AUTO_INCREMENT NOT NULL,
  webhook_id                BIGINT,
  resource_type             VARCHAR(20),
  resource_id               VARCHAR(255),
  thread_id                 VARCHAR(2000),
  created_at                DATETIME,
  CONSTRAINT pk_webhook_thread PRIMARY KEY (id),
  CONSTRAINT fk_webhook_thread_webhook FOREIGN KEY (webhook_id) REFERENCES webhook (id) ON DELETE CASCADE
  )
  row_format=compressed, key_block_size=8
;

CREATE index ix_webhook_thread_webhook_1 ON webhook_thread (webhook_id);
CREATE index ix_webhook_thread_resource_2 ON webhook_thread (resource_type, resource_id);

# --- !Downs
DROP TABLE webhook_thread;
