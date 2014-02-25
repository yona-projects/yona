# --- !Ups

ALTER TABLE notification_event DROP COLUMN IF EXISTS url_to_view;

# --- !Downs

ALTER TABLE notification_event ADD COLUMN url_to_view varchar(255);
