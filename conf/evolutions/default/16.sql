# --- !Ups

ALTER TABLE tag DROP CONSTRAINT IF EXISTS uq_tag_category_name;
ALTER TABLE tag DROP CONSTRAINT IF EXISTS uq_label_category_name;
ALTER TABLE tag ADD CONSTRAINT uq_label_category_name UNIQUE (category, name);
ALTER TABLE tag DROP CONSTRAINT IF EXISTS pk_tag;
ALTER TABLE tag DROP CONSTRAINT IF EXISTS pk_label;
ALTER TABLE tag ADD CONSTRAINT pk_label PRIMARY KEY (ID);
ALTER TABLE tag RENAME TO label;

ALTER TABLE project_tag ALTER COLUMN tag_id RENAME TO label_id;
ALTER TABLE project_tag RENAME TO project_label;

DROP SEQUENCE IF EXISTS label_seq;
CREATE SEQUENCE label_seq START WITH tag_seq.nextval;
DROP SEQUENCE IF EXISTS tag_seq;

# --- !Downs

ALTER TABLE label DROP CONSTRAINT IF EXISTS uq_label_category_name;
ALTER TABLE label DROP CONSTRAINT IF EXISTS uq_tag_category_name;
ALTER TABLE label ADD CONSTRAINT uq_tag_category_name UNIQUE (category, name);
ALTER TABLE label DROP CONSTRAINT IF EXISTS pk_label;
ALTER TABLE label DROP CONSTRAINT IF EXISTS pk_tag;
ALTER TABLE label ADD CONSTRAINT pk_tag PRIMARY KEY (ID);
ALTER TABLE label RENAME TO tag;

ALTER TABLE project_label ALTER COLUMN label_id RENAME TO tag_id;
ALTER TABLE project_label RENAME TO project_tag;

DROP SEQUENCE IF EXISTS tag_seq;
CREATE SEQUENCE tag_seq START WITH label_seq.nextval;
DROP SEQUENCE IF EXISTS label_seq;
