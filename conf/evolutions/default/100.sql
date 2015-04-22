# --- !Ups

alter table notification_event add column tmp_resource_id varchar(255);
update notification_event set tmp_resource_id = cast(resource_id as varchar(255));
alter table notification_event drop column if exists resource_id;
alter table notification_event alter column tmp_resource_id rename to resource_id;

# --- !Downs

-- Drop all records whose resource_id is not a number then restore the type of
-- resource_id to bigint.
delete from notification_event where resource_id regexp '[^0-9]';
alter table notification_event alter column resource_id bigint;
