package controllers;

/*
 * @author: Hwi Ahn
 */

import java.util.regex.Pattern;

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
		
		//약관 동의  체크
		if(!"true".equals(filledNewProjectForm.field("accept").value())) {
			filledNewProjectForm.reject("accept", "반드시 이용 약관에 동의하여야 합니다.");
	    }
		
		//올바른 이름 검사		
		if(!Pattern.matches("^[a-zA-Z0-9]*$", filledNewProjectForm.field("name").value())){
			filledNewProjectForm.reject("name", "올바른 이름을 입력해야 합니다.");
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
