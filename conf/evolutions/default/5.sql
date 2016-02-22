# --- !Ups
# --- special code for MariaDB Only
#

alter table organization add column lower_name varchar(255) as (lower(name)) PERSISTENT;
create unique index ix_organization_lower_name on organization(lower_name);


# --- !Downs
alter table organization drop column lower_name;
drop index ix_organization_lower_name if EXISTS ;
