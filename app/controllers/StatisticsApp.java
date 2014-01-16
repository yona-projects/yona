package controllers;

import actions.NullProjectCheckAction;
import models.Project;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;
import views.html.project.statistics;

public class StatisticsApp extends Controller {

    @With(NullProjectCheckAction.class)
    public static Result statistics(String userName, String projectName) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        return ok(statistics.render("statistics", project));
    }

}
