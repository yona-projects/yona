# --- !Ups

UPDATE project p SET p.organization_id =
(
  SELECT o.id
  FROM organization o
  WHERE UPPER(p.owner)  = UPPER(o.name)
)
WHERE p.id IN
(
  SELECT p.id
  FROM project p LEFT JOIN organization o ON UPPER(p.owner) = UPPER(o.name)
  WHERE UPPER(p.owner)  = UPPER(o.name) AND p.organization_id IS NULL
);

# --- !Downs
