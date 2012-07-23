package controllers;

import models.User;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.index;

public class Application extends Controller {

    public static Result index() {
        User loginUser = User.findById(1l);
        UserApp.authenticate(loginUser);
        return ok(index.render("Your new application is ready."));
    }

}