# --- !Ups
ALTER TABLE project ADD COLUMN is_code_accessible_member_only tinyint(1) default 0;

# --- !Downs
ALTER TABLE project DROP COLUMN is_code_accessible_member_only;
