# --- !Ups

ALTER TABLE issue add column due_date timestamp;

# --- !Downs
ALTER TABLE issue drop column due_date;
