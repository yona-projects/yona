# --- !Ups

create table property (
  id                        bigint not null,
  name                      varchar(255),
  value                     varchar(4000),
  constraint pk_property primary key (id))
;

create sequence property_seq;

# --- !Downs

drop table if exists property;

drop sequence if exists property_seq;
