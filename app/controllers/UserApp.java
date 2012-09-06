package controllers;

import models.*;
import play.data.*;
import play.mvc.*;
import utils.*;
import views.html.*;

import java.util.regex.*;

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
            if(user.id == null) user = User.findByLoginId(user.loginId);
            setUserInfoInSession(user);
            return redirect(routes.Application.index());
        } else {
            flash(Constants.WARNING, "user.login.failed");
            return redirect(routes.UserApp.login());
        }
    }
    
    public static Result signup() {
        return ok(signup.render("title.signup", form(User.class)));
    }
    
    public static Result saveUser() {
        Form<User> newUserForm = form(User.class).bindFromRequest();
        validate(newUserForm);
        if(newUserForm.hasErrors())
            return badRequest(signup.render("title.signup", newUserForm));
        else {
            User user = newUserForm.get();
            user.save();
            setUserInfoInSession(user);
            return redirect(routes.Application.index());
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
    
    public static void validate(Form<User> newUserForm) {
        if(newUserForm.field("loginId").value() == "") {
            newUserForm.reject("loginId", "user.wrongloginId.alert");
        }
        
        if(newUserForm.field("password").value() == "") {
            newUserForm.reject("password", "user.wrongPassword.alert");
        }
        
        if(User.isLoginId(newUserForm.field("loginId").value())){
           newUserForm.reject("loginId", "user.loginId.duplicate"); 
        }
        
        if(!Pattern.compile("^[a-zA-Z0-9_]*$").matcher(newUserForm.field("loginId").value()).find()) {
           newUserForm.reject("loginId", "user.wrongloginId.alert");  
        }
        
        if(!Pattern.compile("[0-9a-zA-Z]+(.[_a-z0-9-]+)*@(?:\\w+\\.)+\\w+$").matcher(newUserForm.field("email").value()).find()) {
            newUserForm.reject("email", "user.wrongEmail.alert");  
        }
    }
}
