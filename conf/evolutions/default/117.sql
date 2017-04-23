# --- !Ups
CREATE TABLE user_verification (
  id                        BIGINT NOT NULL,
  user_id                   BIGINT,
  login_id                  VARCHAR(255),
  verification_code         VARCHAR(255),
  timestamp                 BIGINT,
  CONSTRAINT pk_user_verification PRIMARY KEY (id),
  CONSTRAINT fk_user_verification_user FOREIGN KEY (user_id) REFERENCES n4user (id) on DELETE CASCADE
);

CREATE index ix_user_verification_user_1 ON user_verification (user_id);
CREATE index ix_user_verification_user_2 ON user_verification (login_id, verification_code);

create sequence user_verification_seq;

# --- !Downs
DROP sequence user_verification_seq;
DROP TABLE user_verification;
