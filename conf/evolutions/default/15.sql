# --- !Ups
CREATE TABLE user_verification (
  id                        BIGINT AUTO_INCREMENT NOT NULL,
  user_id                   BIGINT,
  login_id                  VARCHAR(255),
  verification_code         VARCHAR(255),
  timestamp                 BIGINT,
  CONSTRAINT pk_user_verification PRIMARY KEY (id),
  CONSTRAINT fk_user_verification_user FOREIGN KEY (user_id) REFERENCES n4user (id) on DELETE CASCADE
)
row_format=compressed, key_block_size=8;

CREATE index ix_user_verification_user_1 ON user_verification (user_id);
CREATE index ix_user_verification_user_2 ON user_verification (login_id, verification_code);

# --- !Downs
DROP TABLE user_verification;
