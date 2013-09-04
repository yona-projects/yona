# --- !Ups
ALTER TABLE notification_event ALTER COLUMN notification_type RENAME TO event_type;

# --- !Downs
ALTER TABLE notification_event ALTER COLUMN event_type RENAME TO notification_type;
