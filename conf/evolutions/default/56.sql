# --- !Ups

update n4user set state = 'ACTIVE' where state is null;

# --- !Downs
