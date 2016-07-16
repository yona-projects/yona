# --- !Ups
ALTER TABLE project_visitation DROP FOREIGN KEY fk_project_visitation_project_31;
alter table project_visitation add constraint fk_project_visitation_project_31 foreign key (project_id) references project (id) on delete CASCADE on update CASCADE;

# --- !Downs

