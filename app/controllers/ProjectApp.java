package controllers;

import java.io.File;

import models.Project;
import models.ProjectUser;
import models.Role;
import models.User;
import models.enumeration.RoleType;
import play.Logger;
import play.data.Form;
import play.db.ebean.Transactional;
import play.mvc.Controller;
import play.mvc.Http.MultipartFormData;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Result;
import playRepository.RepositoryService;
import utils.AccessControl;
import utils.Constants;
import views.html.project.memberList;
import views.html.project.newProject;
import views.html.project.projectHome;
import views.html.project.setting;
import views.html.projectList;

import com.avaje.ebean.Page;

/**
 * @author "Hwi Ahn"
 */
public class ProjectApp extends Controller {
	public static final String[] LOGO_TYPE = {"jpg", "png", "gif", "bmp"};

    public static Project getProject(String userName, String projectName) {
        return Project.findByNameAndOwner(userName, projectName);
    }

    public static Result project(String userName, String projectName) {
        Project project = ProjectApp.getProject(userName, projectName);
        if (!AccessControl.isAllowed(session().get("userId"), project)) {
            return unauthorized(views.html.project.unauthorized.render(project));
        }

        return ok(projectHome.render("title.projectHome",
                getProject(userName, projectName)));
    }

    public static Result newProject() {
        if (session().get(UserApp.SESSION_USERID) == null) {
            flash(Constants.WARNING, "user.login.alert");
            return redirect(routes.Application.index());
        } else
            return ok(newProject
                    .render("title.newProject", form(Project.class)));
    }

    public static Result setting(String userName, String projectName) {
        Project project = getProject(userName, projectName);
        if (!AccessControl.isAllowed(session().get("userId"), project)) {
            return unauthorized(views.html.project.unauthorized.render(project));
        }

        Form<Project> projectForm = form(Project.class).fill(project);
        return ok(setting.render("title.projectSetting", projectForm, project));
    }

    @Transactional
    public static Result saveProject() throws Exception {
        Form<Project> filledNewProjectForm = form(Project.class)
                .bindFromRequest();
        if(request().body().asFormUrlEncoded().get("accept") == null){
            flash(Constants.WARNING, "project.new.agreement.alert");
            return badRequest(newProject.render("title.newProject",
                    filledNewProjectForm));
        }

        if (Project.isProject(UserApp.currentUser().loginId,
                filledNewProjectForm.field("name").value())) {
            flash(Constants.WARNING, "project.name.duplicate");
            filledNewProjectForm.reject("name");
            return badRequest(newProject.render("title.newProject",
                    filledNewProjectForm));
        } else if (filledNewProjectForm.hasErrors()) {
            filledNewProjectForm.reject("name");
            flash(Constants.WARNING, "project.name.alert");
            return badRequest(newProject.render("title.newProject",
                    filledNewProjectForm));
        } else {
            Project project = filledNewProjectForm.get();
            project.owner = UserApp.currentUser().loginId;
            ProjectUser.assignRole(UserApp.currentUser().id,
                    Project.create(project), RoleType.MANAGER);

            RepositoryService.createRepository(project);

            return redirect(routes.ProjectApp.project(project.owner,
                    project.name));
        }
    }

    public static Result saveSetting(String userName, String projectName) {
        Form<Project> filledUpdatedProjectForm = form(Project.class)
                .bindFromRequest();
        Project project = filledUpdatedProjectForm.get();
          
        if(!ProjectUser.isManager(UserApp.currentUser().id, project.id)){
            flash(Constants.WARNING, "project.member.isManager");
            return redirect(routes.ProjectApp.setting(userName, project.name));
        }

        if (!Project.projectNameChangeable(project.id, userName, project.name)) {
            flash(Constants.WARNING, "project.name.duplicate");
            filledUpdatedProjectForm.reject("name");
        }

        MultipartFormData body = request().body().asMultipartFormData();
        FilePart filePart = body.getFile("logoPath");
        
        if (filePart != null) {
        	if(!isImageFile(filePart.getFilename())) {
        		flash(Constants.WARNING, "project.logo.alert");
        		filledUpdatedProjectForm.reject("logoPath");
        	}
        	else if (filePart.getFile().length() > 1048576) {
                flash(Constants.WARNING, "project.logo.fileSizeAlert");
                filledUpdatedProjectForm.reject("logoPath");
            } else {
                String string = filePart.getFilename();
                string = string.substring(string.lastIndexOf("."));

                File file = new File(Constants.DEFAULT_LOGO_PATH + projectName
                        + string);
                if (file.exists())
                    file.delete();
                filePart.getFile().renameTo(file);

                project.logoPath = projectName + string;
            }
        }

        if (filledUpdatedProjectForm.hasErrors()) {
            return badRequest(setting.render("title.projectSetting",
                    filledUpdatedProjectForm, Project.findById(project.id)));
        } else {
            Logger.debug(project.siteurl);

            project.update();
            return redirect(routes.ProjectApp.setting(userName, project.name));
        }
    }

    public static boolean isImageFile(String filename) {
    	boolean isImageFile = false;
    	for(String suffix : LOGO_TYPE){
    		if(filename.toLowerCase().endsWith(suffix)) 
    			isImageFile=true;
    		}
    	return isImageFile;
    }
    
    public static Result deleteProject(String userName, String projectName) throws Exception {
        Project project = getProject(userName, projectName);
        if (ProjectUser.isManager(UserApp.currentUser().id, project.id)) {
            RepositoryService.deleteRepository(userName, projectName, project.vcs);
            Project.delete(project.id);
            return redirect(routes.Application.index());
        } else {
            flash(Constants.WARNING, "project.member.isManager");
            return redirect(routes.ProjectApp.setting(userName, projectName));
        }
    }

    public static Result members(String userName, String projectName) {
        Project project = getProject(userName, projectName);
        return ok(memberList.render("title.memberList",
                ProjectUser.findMemberListByProject(project.id), project,
                Role.getActiveRoles()));
    }

    public static Result newMember(String userName, String projectName) {
        User user = User
                .findByLoginId(form(User.class).bindFromRequest().get().loginId);
        Project project = getProject(userName, projectName);
        if (!ProjectUser.isManager(UserApp.currentUser().id, project.id)){
            flash(Constants.WARNING, "project.member.isManager");
            return redirect(routes.ProjectApp.members(userName, projectName));
        } else if (user == null) {
            flash(Constants.WARNING, "project.member.notExist");
            return redirect(routes.ProjectApp.members(userName, projectName));
        } else if (!ProjectUser.isMember(user.id, project.id)){
            ProjectUser.assignRole(user.id, project.id, RoleType.MEMBER);
        } else{
            flash(Constants.WARNING, "project.member.alreadyMember");
        }
        return redirect(routes.ProjectApp.members(userName, projectName));
    }

    public static Result deleteMember(String userName, String projectName,
            Long userId) {
        Long projectId = getProject(userName, projectName).id;
        if (ProjectUser.isManager(UserApp.currentUser().id, projectId)) {
            ProjectUser.delete(userId, projectId);
            return redirect(routes.ProjectApp.members(userName, projectName));
        } else {
            flash(Constants.WARNING, "project.member.isManager");
            return redirect(routes.ProjectApp.members(userName, projectName));
        }
    }

    public static Result updateMember(String userName, String projectName, Long userId) {
        Long projectId = getProject(userName, projectName).id;
        if(ProjectUser.isManager(UserApp.currentUser().id, projectId)){
            ProjectUser.assignRole(userId, projectId, form(Role.class)
                    .bindFromRequest().get().id);
            if(ProjectUser.checkOneMangerPerOneProject(projectId)){
                return redirect(routes.ProjectApp.members(userName, projectName));
            } else {
                Logger.info("test12");
                ProjectUser.assignRole(userId, projectId, RoleType.MANAGER);
                flash(Constants.WARNING, "project.member.isManager");
                return redirect(routes.ProjectApp.members(userName, projectName));
            }
        } else {
            flash(Constants.WARNING, "project.member.isManager");
            return redirect(routes.ProjectApp.members(userName, projectName));
        }
    }

    public static Result projects(String filter) {
        Page<Project> projects = Project.findByName(filter, 25, 0);
        int openNum = Project.getOpenProjectNum(filter); 
        return ok(projectList.render("title.siteList", projects, filter, openNum));
    }
}
