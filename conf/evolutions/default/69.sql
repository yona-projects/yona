# --- !Ups

alter table comment_thread add created_date timestamp;

# --- !Downs

alter table comment_thread drop column created_date;
