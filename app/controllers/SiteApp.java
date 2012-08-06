package controllers;

import models.User;
import models.enumeration.Direction;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.site.*;

public class SiteApp extends Controller {
    
    public static Result setting() {
        return ok(setting.render("title.siteSetting"));
    }
    
    public static Result userList() {
        
        return ok(userList.render("title.siteSetting"));
    }
    
    public static Result projectList() {
        return TODO;
    }
    
    public static Result softwareMap() {
        return TODO;
    }
}
