# --- !Ups

ALTER TABLE notification_event DROP COLUMN message;

# --- !Downs

ALTER TABLE notification_event ADD COLUMN message CLOB;
