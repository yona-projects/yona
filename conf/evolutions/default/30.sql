# --- !Ups
ALTER TABLE notification_event ALTER COLUMN type RENAME TO notification_type;

# --- !Downs
ALTER TABLE notification_event ALTER COLUMN notification_type RENAME TO type;
