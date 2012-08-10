package controllers;

import models.Project;
import models.ProjectUser;
import models.Role;
import models.User;
import utils.Constants;

import play.data.Form;
import java.io.*;
import org.eclipse.jgit.lib.*;
import org.tigris.subversion.javahl.ClientException;

import play.mvc.Controller;
import play.mvc.Http.MultipartFormData;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Result;
import views.html.project.newProject;
import views.html.project.projectHome;
import views.html.project.setting;
import views.html.project.memberList;

import java.io.File;

/**
 * @author "Hwi Ahn"
 */
public class ProjectApp extends Controller {

    public static Result project(String projectName) {
        return ok(projectHome.render("title.projectHome",
        		getProject(projectName)));
    }

    public static Result newProject() {
        if(session().get(UserApp.SESSION_USERID) == null){
            flash(Constants.WARNING, "user.login.alert");
            return redirect(routes.Application.index());
        } else 
            return ok(newProject.render("title.newProject", form(Project.class)));
    }

    public static Result setting(String projectName) {
        Form<Project> projectForm = form(Project.class).fill(
                Project.findByName(projectName));
        return ok(setting.render("title.projectSetting", projectForm,
                Project.findByName(projectName)));
    }

    public static Result saveProject() throws IOException, ClientException {
        Form<Project> filledNewProjectForm = form(Project.class)
                .bindFromRequest();

        if (filledNewProjectForm.hasErrors()) {
            return badRequest(newProject.render("title.newProject",
                    filledNewProjectForm));
        } else {
            Project project = filledNewProjectForm.get();
            project.owner = UserApp.currentUser().loginId;
            ProjectUser.assignRole(UserApp.currentUser().id,
                    Project.create(project), Role.MANAGER);

            // create Repository
            if (project.vcs.equals("GIT")) {
                Repository repository = new RepositoryBuilder().setGitDir(
                        new File(GitApp.REPO_PREFIX + project.name + ".git"))
                        .build();
                boolean bare = true;
                repository.create(bare); // create bare repository
            } else if (project.vcs.equals("Subversion")) {
                String svnPath = new File(SvnApp.REPO_PREFIX + project.name).getAbsolutePath();
                new org.tigris.subversion.javahl.SVNAdmin().create(svnPath, false, false, null, "fsfs");
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
        
        if (filePart != null) {
            if (filePart.getFile().length() > 1048576) {
                flash(Constants.WARNING, "project.logo.fileSizeAlert");
                filledUpdatedProjectForm.reject("logoPath");
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
            return badRequest(setting.render("title.projectSetting", filledUpdatedProjectForm,
                    Project.findByName(projectName)));
        } else {
            project.update();
            return redirect(routes.ProjectApp.setting(project.name));
        }
    }

    public static Result deleteProject(String projectName) {
        Project.delete(Project.findByName(projectName).id);
        return redirect(routes.Application.index());
    }

    public static Result members(String projectName) {
        Project project = Project.findByName(projectName);
        return ok(memberList.render("title.memberList", ProjectUser.findMemberListByProject(project.id), project,
                Role.getAllProjectRoles()));
    }

    public static Result newMember(String projectName) {
        User user = User
                .findByLoginId(form(User.class).bindFromRequest().get().loginId);
        Project project = Project.findByName(projectName);
        if(!ProjectUser.isMember(user.id, project.id))
            ProjectUser.assignRole(user.id, project.id, Role.MEMBER);
        else    
            flash(Constants.WARNING, "project.member.alreadyMember");
        return redirect(routes.ProjectApp.members(projectName));
    }

    public static Result deleteMember(Long userId, String projectName) {
        Long projectId = Project.findByName(projectName).id;
        if (isManager(userId, projectId)) {
            ProjectUser.delete(userId, projectId);
            return redirect(routes.ProjectApp.members(projectName));
        } else {
            flash(Constants.WARNING, "project.member.isManager");
            return redirect(routes.ProjectApp.members(projectName));
        }
    }

    public static Result updateMember(Long userId, String projectName) {
        Long projectId = Project.findByName(projectName).id;
        if (isManager(userId, projectId)) {
             ProjectUser.assignRole(userId, projectId,
             form(Role.class).bindFromRequest()
             .get().id);
            return redirect(routes.ProjectApp.members(projectName));
        } else {
            flash(Constants.WARNING, "project.member.isManager");
            return redirect(routes.ProjectApp.members(projectName));
        } 
    }

    public static boolean isManager(Long userId, Long projectId) {
        if(Role.findRoleByIds(userId, projectId).id.equals(Role.MANAGER))
            return ProjectUser.isManager(projectId);
        else
            return true;
        
    }
    
    public static Project getProject(String projectName) {
    	return Project.findByName(projectName);
    }
}
