package controllers;

import play.mvc.Controller;
import play.mvc.Result;

public class StatisticsApp extends Controller {
	public static Result statistics() {
		return ok(views.html.underconstruction.render("statistics"));
	}
}
