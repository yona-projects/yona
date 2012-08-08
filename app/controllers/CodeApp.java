package controllers;

import java.io.IOException;

import org.eclipse.jgit.api.errors.*;

import models.Project;
import play.mvc.*;
import views.html.code.*;

public class CodeApp extends Controller {

    public static Result view(String projectName, String path) throws IOException {
        String vcs = Project.findByName(projectName).vcs;
        if (vcs.equals("GIT")) {
            return GitApp.viewCode(projectName);
        } else {
            return status(501, vcs + " is not supported!");
        }
    }
    
    public static Result ajaxRequest(String projectName, String path) throws IOException, NoHeadException, GitAPIException {
        return GitApp.ajaxRequest(projectName, path);
    }
}
