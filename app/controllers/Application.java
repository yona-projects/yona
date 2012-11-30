package controllers;

import java.util.List;

import models.Project;

import org.h2.util.StringUtils;

import play.cache.Cached;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.index;

public class Application extends Controller {

	@Cached(key = "index")
    public static Result index() {  
    	UserApp.isRememberMe();
    	
        if (session().containsKey("userId")) {        	
        	String userId = session().get("userId");
        	if(StringUtils.isNumber(userId)) {
        		List<Project> projects = Project.findProjectsByMember(Long.parseLong(userId));
        		return ok(index.render(projects));
        	}
        }

        return ok(index.render(null));
    }

    public static Result jsMessages() {
        return ok(jsmessages.JsMessages.generate("Messages")).as("application/javascript");
    }
}