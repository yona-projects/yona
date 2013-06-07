package controllers;

import models.Project;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.project.statistics;

public class StatisticsApp extends Controller {
	public static Result statistics(String userName, String projectName) {
		Project project = Project.findByOwnerAndProjectName(userName, projectName);
		if (project == null) {
		    return notFound();
		}
		return ok(statistics.render("statistics", project));
	}
}
