# --- !Ups

ALTER TABLE n4user ADD COLUMN state VARCHAR(15);
ALTER TABLE n4user ADD COLUMN last_state_modified_date TIMESTAMP;
UPDATE n4user SET state='LOCKED' WHERE is_locked=true;
UPDATE n4user SET state='ACTIVE' WHERE is_locked=false;
ALTER TABLE n4user DROP COLUMN is_locked;
ALTER TABLE n4user ADD CONSTRAINT ck_n4user_state check (state in ('ACTIVE', 'LOCKED', 'DELETED'));

# --- !Downs

ALTER TABLE n4user DROP CONSTRAINT IF EXISTS ck_n4user_state;
ALTER TABLE n4user ADD COLUMN is_locked BOOLEAN DEFAULT FALSE;
UPDATE n4user SET is_locked=true WHERE state='LOCKED';
UPDATE n4user SET is_locked=false WHERE state='ACTIVE';
ALTER TABLE n4user DROP COLUMN state;
ALTER TABLE n4user DROP COLUMN last_state_modified_date;
