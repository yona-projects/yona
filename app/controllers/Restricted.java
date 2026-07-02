package controllers;

import models.UserCredential;
import utils.LegacyController;
import play.mvc.Result;
import play.mvc.Security;
import views.html.restricted;

@Security.Authenticated(Secured.class)
public class Restricted extends LegacyController {

	public Result index() {
		final UserCredential localUser = Application.getLocalUser(session());
		return ok(restricted.render(localUser));
	}
}
