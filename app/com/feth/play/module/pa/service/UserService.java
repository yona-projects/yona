package com.feth.play.module.pa.service;

import com.feth.play.module.pa.user.AuthUser;
import com.feth.play.module.pa.user.AuthUserIdentity;

public interface UserService {
    Object save(AuthUser authUser);

    Object getLocalIdentity(AuthUserIdentity identity);

    AuthUser merge(AuthUser newUser, AuthUser oldUser);

    AuthUser link(AuthUser oldUser, AuthUser newUser);

    AuthUser update(AuthUser knownUser);
}
