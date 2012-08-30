package controllers;

import java.util.List;

import models.Project;
import play.mvc.*;
import views.html.index;

public class Application extends Controller {

    public static Result index() {
        List<Project> projects = Project.findProjectsByMember(Long.parseLong(session().get("userId")));
        return ok(index.render(projects));
    }

}