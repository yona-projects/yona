# --- !Ups

ALTER TABLE posting ADD COLUMN notice BOOLEAN DEFAULT FALSE;
UPDATE posting SET notice = FALSE WHERE notice IS NULL;

# --- !Downs

ALTER TABLE posting DROP COLUMN notice;
