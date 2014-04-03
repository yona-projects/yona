# --- !Ups

update project set project_scope = 'PUBLIC' where is_public = true;
update project set project_scope = 'PRIVATE' where is_public = false;
alter table project drop column is_public;

# --- !Downs

alter table project add column is_public boolean;
update project set is_public = true where project_scope = 'PUBLIC';
update project set is_public = false where project_scope = 'PRIVATE';
