package controllers;

import models.User;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.index;

public class UserApp extends Controller {
    public static final String SESSION_USERID = "userId";
    public static final String SESSION_USERNAME = "userName";

    public static Result login() {
        return ok(index.render("Your new application is ready."));
    }

    public static void authenticate(User user) {
        User authenticate = User.authenticate(user);
        if (authenticate != null) {
            setUserInfoInSession(authenticate);
        }
    }

    public static void setUserInfoInSession(User user) {
        session(SESSION_USERID, String.valueOf(user.id));
        session(SESSION_USERNAME, user.name);
    }

    public static User currentUser() {
        String userId = session().get(SESSION_USERID);
        if (userId == null) {
            throw new IllegalStateException("please login");
        }
        return User.findById(Long.valueOf(userId));
    }
}
