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

    public static Result enroll(String userName, String projectName) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        if(project == null) {
            return badProject(userName, projectName);
        }

        User user = UserApp.currentUser();
        if(user.isAnonymous()) {
            flash(Constants.WARNING, "user.login.alert");
            return redirect(routes.UserApp.loginForm());
        }

        user.enroll(project);

        return redirect(request().getHeader(Http.HeaderNames.REFERER));
    }

    public static Result cancelEnroll(String userName, String proejctName) {
        Project project = Project.findByOwnerAndProjectName(userName, proejctName);
        if(project == null) {
            return badProject(userName, proejctName);
        }

        User user = UserApp.currentUser();
        if(user.isAnonymous()) {
            flash(Constants.WARNING, "user.login.alert");
            return redirect(routes.UserApp.loginForm());
        }

        user.cancleEnroll(project);

        return redirect(request().getHeader(Http.HeaderNames.REFERER));
    }

    private static Result badProject(String userName, String projectName) {
        return badRequest("No project matches given user name '" + userName + "' and project name '" + projectName + "'");
    }

}
