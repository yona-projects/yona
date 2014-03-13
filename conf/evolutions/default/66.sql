# --- !Ups

INSERT INTO role (id, name, active) values (3, 'sitemanager', true);

# --- !Downs

DELETE FROM role where id = 3;
