package controllers;

import play.mvc.Controller;
import play.mvc.Result;

public class HelpApp extends Controller {
	public static Result help() {
		return ok(views.html.underconstruction.render("help"));
	}
}
