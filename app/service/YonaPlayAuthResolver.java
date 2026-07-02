package service;

import com.feth.play.module.pa.Resolver;
import com.feth.play.module.pa.exceptions.AccessDeniedException;
import com.feth.play.module.pa.exceptions.AuthException;
import controllers.routes;
import play.mvc.Call;

public class YonaPlayAuthResolver extends Resolver {
    @Override
    public Call login() {
        return routes.Application.index();
    }

    @Override
    public Call afterAuth() {
        return routes.Application.index();
    }

    @Override
    public Call afterLogout() {
        return routes.Application.index();
    }

    @Override
    public Call auth(final String provider) {
        return routes.Application.oAuth(provider);
    }

    @Override
    public Call onException(final AuthException e) {
        if (e instanceof AccessDeniedException) {
            return routes.Application.oAuthDenied(((AccessDeniedException) e).getProviderKey());
        }

        return super.onException(e);
    }

    @Override
    public Call askLink() {
        return null;
    }

    @Override
    public Call askMerge() {
        return null;
    }
}
