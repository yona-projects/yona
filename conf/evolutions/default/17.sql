# --- !Ups

ALTER TABLE project ADD COLUMN last_pushed_date timestamp;

# --- !Downs

ALTER TABLE project DROP COLUMN last_pushed_date;
