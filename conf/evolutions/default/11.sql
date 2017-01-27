# --- !Ups
CREATE TABLE favorite_project (
  id                        BIGINT AUTO_INCREMENT NOT NULL,
  user_id                   BIGINT,
  project_id                BIGINT,
  owner                     VARCHAR(255),
  project_name              VARCHAR(255),
  CONSTRAINT pk_favorite_project PRIMARY KEY (id),
  CONSTRAINT uq_favorite_project_user_id_project_id_1 UNIQUE (user_id, project_id),
  CONSTRAINT fk_favorite_project_user FOREIGN KEY (user_id) REFERENCES n4user (id) on DELETE CASCADE,
  CONSTRAINT fk_favorite_project_project FOREIGN KEY (project_id) REFERENCES project (id) on DELETE CASCADE
  )
  row_format=compressed, key_block_size=8
;

CREATE index ix_favorite_project_user_1 ON favorite_project (user_id);
CREATE index ix_favorite_project_project_2 ON favorite_project (project_id);

CREATE TABLE favorite_organization (
  id                        BIGINT AUTO_INCREMENT NOT NULL,
  user_id                   BIGINT,
  organization_id           BIGINT,
  organization_name        VARCHAR(255),
  CONSTRAINT pk_favorite_organization PRIMARY KEY (id),
  CONSTRAINT uq_favorite_organization_user_id_organization_id_1 UNIQUE (user_id, organization_id),
  CONSTRAINT fk_favorite_organization_user FOREIGN KEY (user_id) REFERENCES n4user (id) on DELETE CASCADE,
  CONSTRAINT fk_favorite_organization_organization FOREIGN KEY (organization_id) REFERENCES organization (id) on DELETE CASCADE
  )
  row_format=compressed, key_block_size=8
;

CREATE index ix_favorite_organization_user_1 ON favorite_organization (user_id);
CREATE index ix_favorite_organization_organization_2 ON favorite_organization (organization_id);

# --- !Downs
DROP TABLE favorite_project;
DROP TABLE favorite_organization;
