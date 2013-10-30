package controllers;

import play.mvc.Controller;
import play.mvc.Result;
import views.html.help.*;

public class HelpApp extends Controller {
    public static Result help() {
        return ok(toc.render("title.help"));
    }
}
