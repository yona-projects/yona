# --- !Ups
ALTER TABLE n4user ADD COLUMN english_name VARCHAR(255);

# --- !Downs
ALTER TABLE n4user DROP COLUMN english_name;