package controllers;

import java.util.List;

import org.h2.util.StringUtils;

import models.Project;
import play.mvc.*;
import views.html.index;

public class Application extends Controller {

    public static Result index() {
        if (session().containsKey("userId")) {        	
        	String userId = session().get("userId");
        	if(StringUtils.isNumber(userId)) {
        		List<Project> projects = Project.findProjectsByMember(Long.parseLong(userId));
        		return ok(index.render(projects));
        	}
        }
        return ok(index.render(null));
    }
}