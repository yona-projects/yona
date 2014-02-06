# --- !Ups

ALTER TABLE attachment ADD COLUMN created_date DATE;

# --- !Downs

ALTER TABLE attachment DROP COLUMN IF EXISTS created_date;
