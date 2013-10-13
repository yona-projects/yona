# --- !Ups

ALTER TABLE pull_request_event alter column new_value clob;
ALTER TABLE pull_request_event alter column old_value clob;

# --- !Downs

ALTER TABLE pull_request_event alter column new_value varchar(255);
ALTER TABLE pull_request_event alter column old_value varchar(255);

