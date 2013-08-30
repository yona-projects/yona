# --- !Ups

ALTER TABLE issue ADD COLUMN updated_date TIMESTAMP;
ALTER TABLE posting ADD COLUMN updated_date TIMESTAMP;
UPDATE issue SET updated_date=created_date;
UPDATE posting SET updated_date=created_date;

# --- !Downs

ALTER TABLE issue DROP COLUMN updated_date;
ALTER TABLE posting DROP COLUMN updated_date;
