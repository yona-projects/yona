package controllers;

import models.Project;
import models.ProjectUser;
import models.enumeration.ResourceType;
import models.enumeration.RoleType;
import play.data.Form;
import play.db.ebean.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import playRepository.GitRepository;
import utils.AccessControl;
import utils.Constants;
import views.html.project.importing;

import static play.data.Form.form;

/**
 * @author Keesun Baik
 */
public class ImportApp extends Controller {

    public static Result importForm() {
        if (UserApp.currentUser().isAnonymous()) {
            flash(Constants.WARNING, "user.login.alert");
            return redirect(routes.UserApp.loginForm());
        } else {
            return ok(importing.render("title.newProject", form(Project.class)));
        }
    }

    @Transactional
    public static Result newProject() {
        if( !AccessControl.isCreatable(UserApp.currentUser(), ResourceType.PROJECT) ){
            return forbidden("'" + UserApp.currentUser().name + "' has no permission");
        }

        Form<Project> filledNewProjectForm = form(Project.class).bindFromRequest();

        String gitUrl = filledNewProjectForm.data().get("url");
        if(gitUrl == null || gitUrl.trim().isEmpty()) {
            flash(Constants.WARNING, "import.error.empty.url");
            return badRequest(importing.render("title.newProject", filledNewProjectForm));
        }

        if (Project.exists(UserApp.currentUser().loginId, filledNewProjectForm.field("name").value())) {
            flash(Constants.WARNING, "project.name.duplicate");
            filledNewProjectForm.reject("name");
            return badRequest(importing.render("title.newProject", filledNewProjectForm));
        }

        if (filledNewProjectForm.hasErrors()) {
            filledNewProjectForm.reject("name");
            flash(Constants.WARNING, "project.name.alert");
            return badRequest(importing.render("title.newProject", filledNewProjectForm));
        }

        Project project = filledNewProjectForm.get();
        project.owner = UserApp.currentUser().loginId;
        try {
            GitRepository.cloneRepository(gitUrl, project);
            Long projectId = Project.create(project);
            ProjectUser.assignRole(UserApp.currentUser().id, projectId, RoleType.MANAGER);
        } catch (Exception e) {
            flash(Constants.WARNING, "import.error.wrong.url");
            return badRequest(importing.render("title.newProject", filledNewProjectForm));
        }

        return redirect(routes.ProjectApp.project(project.owner, project.name));
    }

}
