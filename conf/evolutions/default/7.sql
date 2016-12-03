# --- !Ups
ALTER TABLE issue ADD COLUMN history longtext;
ALTER TABLE posting ADD COLUMN history longtext;

# --- !Downs
ALTER TABLE issue DROP COLUMN history;
ALTER TABLE posting DROP COLUMN history;
