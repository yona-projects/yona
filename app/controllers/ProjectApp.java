package controllers;

/*
 * @author: Hwi Ahn
 */

import play.mvc.Controller;
import play.mvc.Result;
import play.data.*;

import views.html.project.*;

import models.Project;

import views.html.project.*;

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
		
		if(!"true".equals(filledNewProjectForm.field("accept").value())) {
			filledNewProjectForm.reject("accept", "반드시 이용 약관에 동의하여야 합니다.");
	    }
		
		if(filledNewProjectForm.hasErrors()){
			return badRequest(projectNewPage.render("Create a new project", filledNewProjectForm));
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
