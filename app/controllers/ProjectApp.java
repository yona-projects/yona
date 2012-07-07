package controllers;

import play.mvc.Controller;
import play.mvc.Result;
import views.html.project.projectHome;
import views.html.project.setting;

public class ProjectApp  extends Controller {

    public static Result project(Long id) {
        return ok(projectHome.render("Project Home"));
    }

    public static Result setting(Long id) {
        return ok(setting.render("Setting"));
    }
}
