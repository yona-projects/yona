# --- !Ups
CREATE TABLE user_setting (
  id                        BIGINT NOT NULL,
  user_id                   BIGINT,
  login_default_page        VARCHAR(255),
  CONSTRAINT pk_user_setting PRIMARY KEY (id),
  CONSTRAINT fk_user_setting_user FOREIGN KEY (user_id) REFERENCES n4user (id) on DELETE CASCADE
);

CREATE index ix_user_setting_user_1 ON user_setting (user_id);
create sequence user_setting_seq;

# --- !Downs
DROP TABLE user_setting;
