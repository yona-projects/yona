# --- !Ups
CREATE TABLE user_setting (
  id                        BIGINT AUTO_INCREMENT NOT NULL,
  user_id                   BIGINT,
  login_default_page        VARCHAR(255),
  CONSTRAINT pk_user_setting PRIMARY KEY (id),
  CONSTRAINT fk_user_setting_user FOREIGN KEY (user_id) REFERENCES n4user (id) on DELETE CASCADE
)
row_format=compressed, key_block_size=8;

CREATE index ix_user_setting_user_1 ON user_setting (user_id);

# --- !Downs
DROP TABLE user_setting;
