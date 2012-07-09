package controllers;

/*
 * @author: Hwi Ahn
 */

import play.mvc.Controller;
import play.mvc.Result;
import play.data.Form;

import views.html.project.*;

import models.Project;

import views.html.project.projectHome;
import views.html.project.setting;

public class ProjectApp  extends Controller {
	
	final static Form<Project> newProjectForm = form(Project.class);

	public static Result project(Long id) {
		return ok(projectHome.render("Project Home"));
	}

	public static Result newProject(){
		return ok(projectNewPage.render("Create a new project", newProjectForm));
	}

	public static Result getNewProject(){
		Form<Project> filledNewProjectForm = newProjectForm.bindFromRequest();
		if(filledNewProjectForm.hasErrors()){
			return TODO;
		}else{
			return redirect(routes.ProjectApp.project(
				Project.create(filledNewProjectForm.get()))
			);
		}
	}

	public static Result setting(Long id) {
		return ok(setting.render("Setting"));
	}
}
