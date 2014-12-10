# --- !Ups

CREATE INDEX ix_attachment_container ON attachment (container_type, container_id);

# --- !Downs

DROP INDEX ix_attachment_container;
