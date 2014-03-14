# --- !Ups

delete from notification_event_n4user where n4user_id in (select id from n4user where login_id is null);

delete from n4user where login_id is null;

update issue set assignee_id = null where id in ( select id from issue where assignee_id in (select id from assignee where user_id is null));


delete from assignee where user_id is null;

alter table n4user alter column login_id set not null;

# --- !Downs

alter table n4user alter column login_id drop not null;
