package com.feth.play.module.pa.user;

public interface AuthUser extends AuthUserIdentity {
    default long expires() {
        return -1L;
    }
}
