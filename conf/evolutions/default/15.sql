# --- !Ups

DROP TABLE IF EXISTS label;

# --- !Downs

CREATE TABLE label (
  id                        bigint not null,
  name                      varchar(255),
  color                     varchar(255),
  task_board_id             bigint,
  constraint pk_label primary key (id))
;
