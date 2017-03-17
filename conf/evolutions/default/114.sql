# --- !Ups

create table linked_account (
  id                        bigint not null,
  user_credential_id        bigint,
  provider_user_id          varchar(255),
  provider_key              varchar(255),
  constraint pk_linked_account primary key (id))
;

create table user_credential (
  id                        bigint not null,
  user_id                   bigint,
  login_id                  varchar(255),
  email                     varchar(255),
  name                      varchar(255),
  active                    tinyint(1) default 0,
  email_validated           tinyint(1) default 0,
  constraint pk_users primary key (id),
  CONSTRAINT fk_user_credential_user FOREIGN KEY (user_id) REFERENCES n4user (id) on DELETE CASCADE)
;

create index ix_user_credential_user_id_1 on user_credential (user_id);

alter table linked_account add constraint fk_linked_account_user_1 foreign key (user_credential_id) references user_credential (id) on delete CASCADE;

create index ix_linked_account_user_credential_1 on linked_account (user_credential_id);

create sequence linked_account_seq;
create sequence user_credential_seq; 

# --- !Downs


drop table linked_account;

drop table user_credential;

drop sequence linked_account_seq;
drop sequence user_credential_seq; 

