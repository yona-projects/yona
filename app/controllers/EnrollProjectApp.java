package controllers;

import models.Project;
import models.User;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import utils.Constants;

/**
 * @author Keesun Baik
 */
public class EnrollProjectApp extends Controller {

    public static Result enroll(String loginId, String projectName) {
        Project project = Project.findByOwnerAndProjectName(loginId, projectName);
        if(project == null) {
            return badProject(loginId, projectName);
        }

        User user = UserApp.currentUser();
        if(user.isAnonymous()) {
            flash(Constants.WARNING, "user.login.alert");
            return redirect(routes.UserApp.loginForm());
        }

        user.enroll(project);

        return redirect(request().getHeader(Http.HeaderNames.REFERER));
    }

    public static Result cancelEnroll(String loginId, String proejctName) {
        Project project = Project.findByOwnerAndProjectName(loginId, proejctName);
        if(project == null) {
            return badProject(loginId, proejctName);
        }

        User user = UserApp.currentUser();
        if(user.isAnonymous()) {
            flash(Constants.WARNING, "user.login.alert");
            return redirect(routes.UserApp.loginForm());
        }

        user.cancelEnroll(project);

        return redirect(request().getHeader(Http.HeaderNames.REFERER));
    }

    private static Result badProject(String loginId, String projectName) {
        return badRequest("No project matches given user name '" + loginId + "' and project name '" + projectName + "'");
    }

}
