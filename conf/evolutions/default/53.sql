# --- !Ups

alter table project add column is_using_reviewer_count boolean;
update project set is_using_reviewer_count = false where is_using_reviewer_count is null;

alter table project drop column if exists default_review_point;
alter table project add column default_reviewer_count smallint;
update project set default_reviewer_count = 1 where default_reviewer_count is null;
# --- !Downs

alter table project drop column if exists is_using_reviewer_count;
alter table project drop column if exists default_reviewer_count;
