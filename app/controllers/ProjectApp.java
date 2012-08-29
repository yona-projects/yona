package controllers;

import java.io.File;

import models.*;
import play.data.Form;
import play.db.ebean.Transactional;
import play.mvc.*;
import play.mvc.Http.MultipartFormData;
import play.mvc.Http.MultipartFormData.FilePart;
import playRepository.RepositoryService;
import utils.Constants;
import views.html.project.*;

/**
 * @author "Hwi Ahn"
 */
public class ProjectApp extends Controller {
    
    public static Project getProject(String userName, String projectName) {
        return Project.findByNameAndOwner(userName, projectName);
    }

    public static Result project(String userName, String projectName) {
        return ok(projectHome.render("title.projectHome", getProject(userName, projectName)));
    }

    public static Result newProject() {
        if(session().get(UserApp.SESSION_USERID) == null){
            flash(Constants.WARNING, "user.login.alert");
            return redirect(routes.Application.index());
        } else 
            return ok(newProject.render("title.newProject", form(Project.class)));
    }

    public static Result setting(String userName, String projectName) {
        Project project = getProject(userName, projectName);
        Form<Project> projectForm = form(Project.class).fill(project);
        return ok(setting.render("title.projectSetting", projectForm, project));
    }

	@Transactional
    public static Result saveProject() throws Exception {
        Form<Project> filledNewProjectForm = form(Project.class)
                .bindFromRequest();
        
        if(Project.isProject(UserApp.currentUser().loginId, filledNewProjectForm.field("name").value())) {
            flash(Constants.WARNING, "project.name.duplicate");
            filledNewProjectForm.reject("name");
        }   
        
        if (filledNewProjectForm.hasErrors()) {
            return badRequest(newProject.render("title.newProject",
                    filledNewProjectForm));
        } else {
            Project project = filledNewProjectForm.get();
            project.owner = UserApp.currentUser().loginId;
            ProjectUser.assignRole(UserApp.currentUser().id,
                    Project.create(project), Role.MANAGER);

            // create Repository
            // FIXME 이게 과연 CodeApp의 역활인가?
            RepositoryService.createRepository(project.owner, project.name, project.vcs);

            return redirect(routes.ProjectApp.project(project.owner, project.name));
        }
    }

    public static Result saveSetting(String userName, String projectName) {
        Form<Project> filledUpdatedProjectForm = form(Project.class)
                .bindFromRequest();
        Project project = filledUpdatedProjectForm.get();
        
        if(!Project.projectNameChangeable(project.id, userName, project.name)) {
            flash(Constants.WARNING, "project.name.duplicate");
            filledUpdatedProjectForm.reject("name");
        }
        
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
                    Project.findById(project.id)));
        } else {
            project.update();
            return redirect(routes.ProjectApp.setting(userName, project.name));
        }
    }

    public static Result deleteProject(String userName, String projectName) {
        Project.delete(getProject(userName, projectName).id);
        return redirect(routes.Application.index());
    }

    public static Result members(String userName, String projectName) {
        Project project = getProject(userName, projectName);
        return ok(memberList.render("title.memberList", ProjectUser.findMemberListByProject(project.id), project,
                Role.getActiveRoles()));
    }

    public static Result newMember(String userName, String projectName) {
        User user = User
                .findByLoginId(form(User.class).bindFromRequest().get().loginId);
        if(user == null) {
            flash(Constants.WARNING, "project.member.notExist");
            return redirect(routes.ProjectApp.members(userName, projectName));
        }
        Project project = getProject(userName, projectName);
        if(!ProjectUser.isMember(user.id, project.id))
            ProjectUser.assignRole(user.id, project.id, Role.MEMBER);
        else    
            flash(Constants.WARNING, "project.member.alreadyMember");
        return redirect(routes.ProjectApp.members(userName, projectName));
    }

    public static Result deleteMember(String userName, String projectName, Long userId) {
        Long projectId = getProject(userName, projectName).id;
        if (isManager(userId, projectId)) {
            ProjectUser.delete(userId, projectId);
            return redirect(routes.ProjectApp.members(userName, projectName));
        } else {
            flash(Constants.WARNING, "project.member.isManager");
            return redirect(routes.ProjectApp.members(userName, projectName));
        }
    }

    public static Result updateMember(String userName, String projectName, Long userId) {
        Long projectId = getProject(userName, projectName).id;
        if (isManager(userId, projectId)) {
             ProjectUser.assignRole(userId, projectId,
             form(Role.class).bindFromRequest()
             .get().id);
            return redirect(routes.ProjectApp.members(userName, projectName));
        } else {
            flash(Constants.WARNING, "project.member.isManager");
            return redirect(routes.ProjectApp.members(userName, projectName));
        } 
    }

    public static boolean isManager(Long userId, Long projectId) {
        if(Role.findRoleByIds(userId, projectId).id.equals(Role.MANAGER))
            return ProjectUser.checkOneMangerPerOneProject(projectId);
        else
            return true;
        
    }
}
