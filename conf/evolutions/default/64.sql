# --- !Ups

ALTER TABLE notififcation_event DROP COLUMN IF EXISTS url_to_view;

# --- !Downs

ALTER TABLE notififcation_event ADD COLUMN url_to_view varchar(255); 
