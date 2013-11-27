# --- !Ups
ALTER TABLE attachment ALTER COLUMN container_type TYPE varchar(255);

# --- !Downs

ALTER TABLE attachment ALTER COLUMN container_type TYPE varchar(16);
