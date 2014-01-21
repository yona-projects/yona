# --- !Ups

alter table project drop column if exists watching_count;

# --- !Downs

alter table project add column watching_count smallint;
update project set watching_count = 0;
