# --- !Ups

ALTER TABLE n4user ADD COLUMN lang varchar(255);

# --- !Downs

ALTER TABLE n4user DROP COLUMN lang;
