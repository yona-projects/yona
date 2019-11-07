# --- !Ups
ALTER TABLE project_transfer ADD uq_project_transfer_1 UNIQUE (sender_id, destination, project_id);

# --- !Downs
ALTER TABLE project_transfer DROP INDEX uq_project_transfer_1