package controllers;

import models.User;
import play.mvc.Controller;
import play.mvc.Result;
import utils.Constants;
import views.html.index;
import views.html.login;

public class UserApp extends Controller {
    public static final String SESSION_USERID = "userId";
    public static final String SESSION_USERNAME = "userName";

    public static Result login() {
        return ok(login.render("title.login"));
    }
    
    public static Result logout() {
        session().clear();
        flash(Constants.SUCCESS, "user.logout.success");
        return redirect(routes.Application.index());
    }
    
    public static Result authenticate() {
        User user = form(User.class).bindFromRequest().get();
        
        if(User.authenticate(user)){
            setUserInfoInSession(user);
            return redirect(routes.Application.index());
        } else {
            flash(Constants.WARNING, "user.login.failed");
            return redirect(routes.UserApp.login());
        }
    }

    public static void setUserInfoInSession(User user) {
        if(user.id == null) user = User.findByLoginId(user.loginId);
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
