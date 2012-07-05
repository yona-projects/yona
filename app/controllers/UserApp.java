package controllers;

import play.mvc.Controller;
import play.mvc.Result;
import views.html.index;

public class UserApp extends Controller {
	public static Result login() {
		return ok(index.render("Your new application is ready."));
	}

}
