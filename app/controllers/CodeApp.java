package controllers;

import models.Project;
import play.mvc.*;
import views.html.code.*;

public class CodeApp extends Controller {

    public static Result view(String projectName) {
        String vcs = Project.findByName(projectName).vcs;
        if (vcs.equals("GIT")) {
            return ok(gitView.render("http://localhost:9000/" + projectName,
                    Project.findByName(projectName)));
        } else {
            return status(501, vcs + " is not supported!");
        }
    }
}
