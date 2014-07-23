# --- !Ups
update milestone set due_date = CAST(CAST(due_date as date) || ' 23:59:59' as timestamp);
# --- !Downs
update milestone set due_date = CAST(CAST(due_date as date) || ' 00:00:00' as timestamp);
