package controllers;

import models.Project;
import models.ProjectUser;
import models.enumeration.RoleType;
import play.data.Form;
import play.db.ebean.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import playRepository.GitRepository;
import utils.AccessControl;
import utils.Constants;
import utils.FileUtil;
import views.html.project.importing;

import org.eclipse.jgit.api.errors.*;
import java.io.IOException;

import java.io.File;

import static play.data.Form.form;

public class ImportApp extends Controller {

    /**
     * Git repository에서 코드를 가져와서 프로젝트를 만드는 폼을 보여준다.
     *
     * @return
     */
    public static Result importForm() {
        if (UserApp.currentUser().isAnonymous()) {
            flash(Constants.WARNING, "user.login.alert");
            return redirect(routes.UserApp.loginForm());
        } else {
            return ok(importing.render("title.newProject", form(Project.class)));
        }
    }

    /**
     * 새 프로젝트 시작 폼에 추가로 Git 저장소 URL을 추가로 입력받고
     * 해당 저장소를 clone하여 프로젝트의 Git 저장소를 생성한다.
     *
     * @return
     */
    @Transactional
    public static Result newProject() throws GitAPIException, IOException {
        if( !AccessControl.isCreatable(UserApp.currentUser()) ){
            return forbidden("'" + UserApp.currentUser().name + "' has no permission");
        }

        Form<Project> filledNewProjectForm = form(Project.class).bindFromRequest();

        String gitUrl = filledNewProjectForm.data().get("url");
        if(gitUrl == null || gitUrl.trim().isEmpty()) {
            flash(Constants.WARNING, "project.import.error.empty.url");
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
        String errorMessageKey = null;
        try {
            GitRepository.cloneRepository(gitUrl, project);
            Long projectId = Project.create(project);
            ProjectUser.assignRole(UserApp.currentUser().id, projectId, RoleType.MANAGER);
        } catch (InvalidRemoteException e) {
            // It is not an url.
            errorMessageKey = "project.import.error.wrong.url";
        } catch (JGitInternalException e) {
            // The url seems that does not locate a git repository.
            errorMessageKey = "project.import.error.wrong.url";
        } catch (TransportException e) {
            errorMessageKey = "project.import.error.transport";
        }

        if (errorMessageKey != null) {
            flash(Constants.WARNING, errorMessageKey);
            FileUtil.rm_rf(new File(GitRepository.getGitDirectory(project)));
            return badRequest(importing.render("title.newProject", filledNewProjectForm));
        } else {
            return redirect(routes.ProjectApp.project(project.owner, project.name));
        }
    }

}
