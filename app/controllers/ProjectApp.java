package controllers;

import play.mvc.Controller;
import play.mvc.Result;
import views.html.project.projectHome;

public class ProjectApp  extends Controller {
	
	public static Result project(Long id) {
		return ok(projectHome.render("Project Home"));
	}
}
