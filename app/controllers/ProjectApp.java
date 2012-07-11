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

public class ProjectApp extends Controller {

    public static Result project(Long id) {
        return ok(projectHome.render("프로젝트 홈"));
    }

    public static Result newProject() {
        return ok(newProject.render("새 프로젝트 생성", form(Project.class)));
    }

    public static Result setting(Long id) {
        Form<Project> projectForm = form(Project.class).fill(
                Project.findById(id));
        return ok(setting.render("프로젝트 설정", projectForm, id));
    }

    public static Result saveProject() {
        Form<Project> filledNewProjectForm = form(Project.class).bindFromRequest();

        if (!"true".equals(filledNewProjectForm.field("accept").value())) {
            filledNewProjectForm.reject("accept", "반드시 이용 약관에 동의하여야 합니다.");
        }

        if (filledNewProjectForm.hasErrors()) {
            return badRequest(newProject.render("새 프로젝트 생성",
                    filledNewProjectForm));
        } else {
            Project project = filledNewProjectForm.get();
            project.owner = UserApp.userId();
            return redirect(routes.ProjectApp.project(Project
                    .create(project)));
        }
    }

    public static Result saveSetting(Long id) {
        Form<Project> filledUpdatedProjectForm = form(Project.class)
                .bindFromRequest();

        if (!filledUpdatedProjectForm.field("url").value()
                .startsWith("http://")) {
            filledUpdatedProjectForm.reject("url",
                    "사이트 URL은 http://로 시작하여야 합니다.");
        }

        if (filledUpdatedProjectForm.hasErrors()) {
            return badRequest(setting.render("Setting",
                    filledUpdatedProjectForm, id));
        } else {
            return redirect(routes.ProjectApp.setting(Project.update(
                    filledUpdatedProjectForm.get(), id)));
        }
    }

    public static Result deleteProject(Long id) {
        Form<Project> deletedProject = form(Project.class).bindFromRequest();

        if (!"true".equals(deletedProject.field("acceptDeletion").value())) {
            deletedProject = form(Project.class).fill(Project.findById(id));
            deletedProject.reject("acceptDeletion", "프로젝트 삭제에 동의하여야 합니다.");
            return badRequest(setting.render("Setting", deletedProject, id));
        }

        Project.delete(id);
        return redirect(routes.Application.index());
    }
}
