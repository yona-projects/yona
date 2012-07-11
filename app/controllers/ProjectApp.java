package controllers;

/*
 * @author: Hwi Ahn
 * 
 * 
 */


import play.mvc.Controller;
import play.mvc.Result;
import play.data.*;

import views.html.project.*;

import models.Project;

public class ProjectApp  extends Controller {
	
	final static Form<Project> newProjectForm = form(Project.class);

	public static Result project(Long id) {
		return ok(projectHome.render("Project Home"));
	}

	public static Result newProject() {
		return ok(newProject.render("Create a new project", newProjectForm));
	}

	public static Result setting(Long id) {
		Form<Project> projectForm = form(Project.class).fill(Project.findById(id));
		return ok(setting.render("Setting", projectForm, id));
	}

	public static Result saveProject() {
		Form<Project> filledNewProjectForm = newProjectForm.bindFromRequest();
		
		if(!"true".equals(filledNewProjectForm.field("accept").value())) {
			filledNewProjectForm.reject("accept", "반드시 이용 약관에 동의하여야 합니다.");
	    }
		
		if(filledNewProjectForm.hasErrors()) {
			return badRequest(newProject.render("Create a new project", filledNewProjectForm));
		}else {
			return redirect(routes.ProjectApp.project(
				Project.create(filledNewProjectForm.get()))
			);
		}
	}
	
	public static Result saveSetting(Long id) {
		Form<Project> filledUpdatedProjectForm = newProjectForm.bindFromRequest();
		
		if(!filledUpdatedProjectForm.field("url").value().startsWith("http://")) {
			filledUpdatedProjectForm.reject("url", "사이트 URL은 http://로 시작하여야 합니다.");
		}
		
		if(filledUpdatedProjectForm.hasErrors()) {
			return badRequest(setting.render("Setting", filledUpdatedProjectForm, id));
		}else {
			return redirect(routes.ProjectApp.setting(Project.update(filledUpdatedProjectForm.get(), id)));
		}
	}
	
}
