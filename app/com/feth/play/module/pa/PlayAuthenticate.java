package com.feth.play.module.pa;

import com.feth.play.module.pa.service.UserService;
import com.feth.play.module.pa.user.AuthUser;
import com.feth.play.module.pa.user.SessionAuthUser;
import controllers.UserApp;
import play.mvc.Http;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;

@Singleton
public class PlayAuthenticate {
    private final UserService userService;
    private final Resolver resolver;

    @Inject
    public PlayAuthenticate(UserService userService, Resolver resolver) {
        this.userService = userService;
        this.resolver = resolver;
    }

    public UserService getUserService() {
        return userService;
    }

    public Resolver getResolver() {
        return resolver;
    }

    public boolean isLoggedIn(Map<String, String> session) {
        return getUser(session) != null;
    }

    public boolean isLoggedIn(Http.Session session) {
        return session != null && isLoggedIn(session.data());
    }

    public AuthUser getUser(Map<String, String> session) {
        if (session == null || !session.containsKey(UserApp.SESSION_USERID)) {
            return null;
        }
        return new SessionAuthUser(
                session.get(UserApp.SESSION_USERID),
                session.getOrDefault("pa.provider", "local"),
                session.get(UserApp.SESSION_LOGINID),
                session.get(UserApp.SESSION_USERNAME));
    }

    public AuthUser getUser(Http.Session session) {
        return session == null ? null : getUser(session.data());
    }

    public void logout(Map<String, String> session) {
        if (session != null) {
            session.remove("pa.provider");
            session.remove("pa.id");
            session.remove("pa.url.orig");
        }
    }

    public String storeOriginalUrl(Object requestOrContext) {
        Http.Request request = utils.LegacyRequestContext.currentRequestOrNull();
        String url = request == null ? "/" : request.uri();
        utils.LegacyRequestContext.session().put("pa.url.orig", url);
        return url;
    }
}
