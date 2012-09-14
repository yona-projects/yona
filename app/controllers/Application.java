package controllers;

import java.util.List;

import models.Project;

import org.h2.util.StringUtils;

import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.index;

public class Application extends Controller {

    public static Result index() {  
    	UserApp.isRememberMe();
    	
        if (session().containsKey("userId")) {        	
        	String userId = session().get("userId");
        	if(StringUtils.isNumber(userId)) {
        		List<Project> projects = Project.findProjectsByMember(Long.parseLong(userId));
        		for (Project project : projects) {
            		Logger.debug(project.name);
        		}
        		return ok(index.render(projects));
        	}
        }

        return ok(index.render(null));
    }
}