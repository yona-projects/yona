# --- !Ups

alter table posting add column readme boolean;
update posting set readme = false;

# --- !Downs

alter table posting drop column readme;
