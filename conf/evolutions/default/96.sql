# --- !Ups

create table original_email (
  id                        bigint not null,
  message_id                varchar(255),
  resource_type             varchar(255),
  resource_id               varchar(255),
  handled_date              timestamp,
  constraint uq_original_email_1 unique (resource_type,resource_id),
  constraint pk_original_email primary key (id))
;

create sequence original_email_seq;

create index ix_original_email_message_id_32 on original_email (message_id);
create index ix_original_email_resource_id_33 on original_email (resource_id);
create index ix_original_email_resource_type_34 on original_email (resource_type);

# --- !Downs

drop table if exists original_email;

drop sequence if exists original_email_seq;
