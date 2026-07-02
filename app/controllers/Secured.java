package controllers;

import com.feth.play.module.pa.user.AuthUser;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Security;
import utils.LegacyRequestContext;
import utils.PlayAuthenticateUtil;

import java.util.Optional;

public class Secured extends Security.Authenticator {

	@Override
	public Optional<String> getUsername(final Http.Request req) {
		final AuthUser u = PlayAuthenticateUtil.get().getUser(req.session().data());

		if (u != null) {
			return Optional.of(u.getId());
		} else {
			return Optional.empty();
		}
	}

	@Override
	public Result onUnauthorized(final Http.Request req) {
		if (LegacyRequestContext.isBound()) {
			LegacyRequestContext.flash().put(Application.FLASH_MESSAGE_KEY, "Nice try, but you need to log in first!");
			return redirect(routes.Application.index());
		}

		LegacyRequestContext.begin(req);
		try {
			LegacyRequestContext.flash().put(Application.FLASH_MESSAGE_KEY, "Nice try, but you need to log in first!");
			return LegacyRequestContext.apply(redirect(routes.Application.index()));
		} finally {
			LegacyRequestContext.end();
		}
	}
}
