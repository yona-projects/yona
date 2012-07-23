package controllers;

import models.Project;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Http.MultipartFormData;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Result;
import views.html.project.newProject;
import views.html.project.projectHome;
import views.html.project.setting;

import java.io.File;

/**
 * @author "Hwi Ahn"
 */
public class ProjectApp extends Controller {

    public static final String PROJECT_HOME = "프로젝트 홈";
    public static final String NEW_PROJECT = "새 프로젝트 생성";
    public static final String SETTING = "프로젝트 설정";

    public static Result project(Long id) {
        return ok(projectHome.render(PROJECT_HOME, Project.findById(id)));
    }

    public static Result newProject() {
        return ok(newProject.render(NEW_PROJECT, form(Project.class)));
    }

    public static Result setting(Long id) {
        Form<Project> projectForm = form(Project.class).fill(
            Project.findById(id));
        return ok(setting.render(SETTING, projectForm, id));
    }

    public static Result saveProject() {
        Form<Project> filledNewProjectForm = form(Project.class)
            .bindFromRequest();

        if (!"true".equals(filledNewProjectForm.field("accept").value())) {
            filledNewProjectForm.reject("accept", "반드시 이용 약관에 동의하여야 합니다.");
        }

        if (filledNewProjectForm.hasErrors()) {
            return badRequest(newProject.render(NEW_PROJECT,
                filledNewProjectForm));
        } else {
            Project project = filledNewProjectForm.get();
            project.owner = UserApp.currentUser();
            return redirect(routes.ProjectApp.project(Project.create(project)));
        }
    }

    public static Result saveSetting(Long id) {

        Form<Project> filledUpdatedProjectForm = form(Project.class)
            .bindFromRequest();
        Project project = filledUpdatedProjectForm.get();
        MultipartFormData body = request().body().asMultipartFormData();
        FilePart filePart = body.getFile("logoPath");

        if (!filledUpdatedProjectForm.field("url").value()
            .startsWith("http://")) {
            filledUpdatedProjectForm.reject("url",
                "사이트 URL은 http://로 시작하여야 합니다.");
        }

        if (filePart != null) {
            if (filePart.getFile().length() > 1048576) {
                filledUpdatedProjectForm.reject("logoPath",
                    "이미지 용량은 1MB 이하여야 합니다.");
            } else {
                String string = filePart.getFilename();
                string = string.substring(string.lastIndexOf("."));

                File file = new File("public/uploadFiles/" + Long.toString(id)
                    + string);
                if (file.exists())
                    file.delete();
                filePart.getFile().renameTo(file);

                project.logoPath = Long.toString(id) + string;
            }
        }

        if (filledUpdatedProjectForm.hasErrors()) {
            return badRequest(setting.render(SETTING, filledUpdatedProjectForm,
                id));
        } else {
            return redirect(routes.ProjectApp.setting(Project.update(project,
                id)));
        }
    }

    public static Result deleteProject(Long id) {
        Project.delete(id);
        return redirect(routes.Application.index());
    }
}
