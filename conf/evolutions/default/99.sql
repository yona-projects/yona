# --- !Ups

delete from original_email a where a.id > any (select b.id from original_email b where a.message_id = b.message_id);

alter table original_email add constraint uq_original_email_2 unique (message_id);

# --- !Downs

alter table original_email drop constraint if exists uq_original_email_2;
