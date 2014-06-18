# --- !Ups
alter table pull_request_related_authors drop constraint if exists fk_pull_request_related_authors_1;
alter table pull_request_related_authors drop constraint if exists fk_pull_request_related_authors_2;
drop table if exists pull_request_related_authors;

# --- !Downs
create table pull_request_related_authors (
    pull_request_id BIGINT NOT NULL,
    user_id INT NOT NULL
);
alter table pull_request_related_authors add constraint fk_pull_request_related_authors_1 foreign key (pull_request_id) references pull_request (id) on delete restrict on update restrict;
alter table pull_request_related_authors add constraint fk_pull_request_related_authors_2 foreign key (user_id) references n4user (id) on delete restrict on update restrict;
