# --- !Ups
create unique index uq_n4user_1 on n4user (login_id);
create index ix_notification_event_created on notification_event (created DESC);
create index ix_watch_resource_id_resource_type on watch(resource_id, resource_type);
create index ix_unwatch_resource_id_resource_type on unwatch(resource_id, resource_type);


# --- !Downs
drop index uq_n4user_1 on n4user;
drop index ix_notification_event_created on notification_event;
drop index ix_watch_resource_id_resource_type on watch;
drop index ix_unwatch_resource_id_resource_type on unwatch;
