package com.feth.play.module.pa.controllers;

import com.feth.play.module.pa.PlayAuthenticate;
import play.mvc.Controller;
import play.mvc.Result;

public class Authenticate extends Controller {
    private final PlayAuthenticate playAuthenticate;

    public Authenticate(PlayAuthenticate playAuthenticate) {
        this.playAuthenticate = playAuthenticate;
    }

    public Result authenticate(String provider) {
        play.Logger.warn("OAuth provider '{}' requires a Play 3 compatible provider implementation", provider);
        return redirect(controllers.routes.Application.oAuthDenied(provider));
    }

    public Result logout() {
        playAuthenticate.logout(utils.LegacyRequestContext.session());
        return redirect(playAuthenticate.getResolver().afterLogout());
    }
}
