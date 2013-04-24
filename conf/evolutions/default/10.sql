# --- !Ups
ALTER TABLE N4USER ADD COLUMN is_locked boolean default false;


# --- !Downs
ALTER TABLE N4USER DROP COLUMN is_locked;
