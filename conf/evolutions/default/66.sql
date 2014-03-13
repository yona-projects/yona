# --- !Ups

INSERT INTO role (id, name, active) values (3, 'sitemanager', true);
INSERT INTO role (id, name, active) values (4, 'anonymous', true);
INSERT INTO role (id, name, active) values (5, 'guest', true);

# --- !Downs

DELETE FROM role where id = 3;
DELETE FROM role where id = 4;
DELETE FROM role where id = 5;
