# --- !Ups

ALTER TABLE issue_event alter column new_value clob;
ALTER TABLE issue_event alter column old_value clob;
ALTER TABLE issue_event ADD COLUMN sender_email varchar(255);

# --- !Downs

ALTER TABLE issue_event alter column new_value varchar(255);
ALTER TABLE issue_event alter column old_value varchar(255);
ALTER TABLE issue_event DROP COLUMN IF EXISTS sender_email;
