package controllers;

import java.util.List;

import models.Project;
import play.mvc.*;
import views.html.index;

public class Application extends Controller {

    public static Result index() {
        if (session().containsKey("userId")) {
            List<Project> projects = Project.findProjectsByMember(Long.parseLong(session().get(
                    "userId")));
            return ok(index.render(projects));
        }
        else {
            return ok(index.render(null));
        }
        
    }

}