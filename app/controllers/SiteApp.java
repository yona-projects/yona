package controllers;

import models.Project;
import models.User;
import play.mvc.Controller;
import play.mvc.Result;
import utils.Constants;
import views.html.site.*;

public class SiteApp extends Controller {
    
    public static Result setting() {
        return ok(setting.render("title.siteSetting"));
    }
    
    public static Result userList(int pageNum, String loginId) {
        return ok(userList.render("title.siteSetting", User.findUsers(pageNum, loginId)));
    }
    
    public static Result searchUser() {
        String loginId = form(User.class).bindFromRequest().get().loginId;
        return redirect(routes.SiteApp.userList(0, loginId));
    }
    
    public static Result deleteUser(Long userId) {
        if(Project.isOnlyManager(userId).size() == 0)
            User.findById(userId).delete();
        else
            flash(Constants.WARNING, "site.userList.deleteAlert");
            
        return redirect(routes.SiteApp.userList(0, null));
    }
        
    public static Result projectList() {
        return TODO;
    }
    
    public static Result softwareMap() {
        return TODO;
    }
}
