package controllers;

import models.Project;
import models.ProjectUser;
import models.Role;
import models.User;
import utils.Constants;

import play.data.Form;
import java.io.*;
import org.eclipse.jgit.lib.*;

import play.mvc.Controller;
import play.mvc.Http.MultipartFormData;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Result;
import views.html.project.newProject;
import views.html.project.projectHome;
import views.html.project.setting;
import views.html.project.memberList;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author "Hwi Ahn"
 */
public class ProjectApp extends Controller {
    public static final String PROJECT_HOME = "프로젝트 홈";
    public static final String NEW_PROJECT = "새 프로젝트 생성";
    public static final String SETTING = "프로젝트 설정";
    public static final String MEMBER_LIST = "맴버";

    public static Result project(String projectName) {
        return ok(projectHome.render(PROJECT_HOME,
                Project.findByName(projectName)));
    }

    public static Result newProject() {
        return ok(newProject.render(NEW_PROJECT, form(Project.class)));
    }

    public static Result setting(String projectName) {
        Form<Project> projectForm = form(Project.class).fill(
                Project.findByName(projectName));
        return ok(setting.render(SETTING, projectForm,
                Project.findByName(projectName)));
    }

    public static Result saveProject() throws IOException {
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
            ProjectUser.assignRole(UserApp.currentUser().id,
                    Project.create(project), Role.MANAGER);

            // create Repository
            if (project.vcs.equals("GIT")) {
                Repository repository = new RepositoryBuilder().setGitDir(
                        new File(GitApp.REPO_PREFIX + project.name + ".git"))
                        .build();
                boolean bare = true;
                repository.create(bare); // create bare repository
            } else {
                throw new UnsupportedOperationException("only support git!");
            }

            return redirect(routes.ProjectApp.project(project.name));
        }
    }

    public static Result saveSetting(String projectName) {
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

                File file = new File(Constants.DEFAULT_LOGO_PATH + projectName + string);
                if (file.exists())
                    file.delete();
                filePart.getFile().renameTo(file);

                project.logoPath = projectName + string;
            }
        }

        if (filledUpdatedProjectForm.hasErrors()) {
            return badRequest(setting.render(SETTING, filledUpdatedProjectForm,
                    Project.findByName(projectName)));
        } else {
            return redirect(routes.ProjectApp.setting(Project.update(project,
                    projectName)));
        }
    }

    public static Result deleteProject(String projectName) {
        Project.delete(Project.findByName(projectName).id);
        return redirect(routes.Application.index());
    }

    public static Result memberList(String projectName, boolean noError) {
        Project project = Project.findByName(projectName);
        List<User> users = ProjectUser.findUsersByProject(project.id);
        List<Form<User>> usersList = new ArrayList<Form<User>>();
        for (User user : users) {
            usersList.add(form(User.class).fill(user));
        }
        flash(Constants.WARNING, "project.member.isManager");
        return ok(memberList.render(MEMBER_LIST, usersList, project,
                Role.getAllProjectRoles(), noError));
    }

    public static Result addMember(String projectName) {
        // TODO: 이미 가입되어있는지 여부는 view에서 Javascript로 처리
        User user = User
                .findByLoginId(form(User.class).bindFromRequest().get().loginId);
        ProjectUser.assignRole(user.id, Project.findByName(projectName).id,
                Role.MEMBER);
        return redirect(routes.ProjectApp.memberList(projectName, true));
    }

    public static Result deleteMember(Long userId, String projectName) {
        Long projectId = Project.findByName(projectName).id;
        if (isManager(userId, projectId)) {
            ProjectUser.delete(userId, projectId);
            return redirect(routes.ProjectApp.memberList(projectName, true));
        } else
            return redirect(routes.ProjectApp.memberList(projectName, false));

    }

    public static Result updateMember(Long userId, String projectName) {
        Long projectId = Project.findByName(projectName).id;
        if (isManager(userId, projectId)) {
             ProjectUser.assignRole(userId, projectId,
             form(Role.class).bindFromRequest()
             .get().id);
            return redirect(routes.ProjectApp.memberList(projectName, true));
        } else
            return redirect(routes.ProjectApp.memberList(projectName, false));
    }

    public static boolean isManager(Long userId, Long projectId) {
        if(ProjectUser.findRoleByIds(userId, projectId).id.equals(Role.MANAGER))
            return ProjectUser.isManager(projectId);
        else
            return true;
        
    }
}
