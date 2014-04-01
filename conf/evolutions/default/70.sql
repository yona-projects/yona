# --- !Ups

alter table comment_thread add prev_commit_id varchar(255);

# --- !Downs

alter table comment_thread drop column prev_commit_id;
