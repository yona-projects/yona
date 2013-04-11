# --- !Ups

ALTER TABLE milestone ADD CONSTRAINT uq_milestone_title UNIQUE (title);

# --- !Downs

ALTER TABLE milestone DROP CONSTRAINT uq_milestone_title;