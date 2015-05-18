# --- !Ups

update project_user set role_id = (select id from role where name = 'sitemanager')
where role_id = 0 and user_id = (select admin_id from site_admin);

# --- !Downs
