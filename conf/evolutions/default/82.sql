# --- !Ups
update project set organization_id=null
where id in (select p.id from project p left join organization o on p.organization_id=o.id where p.organization_id is not null and p.owner != o.name);

# --- !Downs
