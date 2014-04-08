package controllers;

import models.Organization;
import models.OrganizationUser;
import models.Project;
import models.ProjectUser;
import models.User;
import models.enumeration.RoleType;
import play.data.Form;
import play.db.ebean.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;
import playRepository.GitRepository;
import utils.AccessControl;
import utils.ErrorViews;
import utils.FileUtil;
import utils.ValidationResult;
import views.html.project.create;
import views.html.project.importing;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.errors.*;

import actions.AnonymousCheckAction;

import java.io.IOException;
import java.io.File;
import java.util.List;

import static play.data.Form.form;

public class ImportApp extends Controller {

    /**
     * Git repository에서 코드를 가져와서 프로젝트를 만드는 폼을 보여준다.
     *
     * @return
     */
    @With(AnonymousCheckAction.class)
    public static Result importForm() {
        List<OrganizationUser> orgUserList = OrganizationUser.findByAdmin(UserApp.currentUser().id);
        return ok(importing.render("title.newProject", form(Project.class), orgUserList));
    }

    /**
     * 새 프로젝트 시작 폼에 추가로 Git 저장소 URL을 추가로 입력받고
     * 해당 저장소를 clone하여 프로젝트의 Git 저장소를 생성한다.
     *
     * @return
     */
    @Transactional
    public static Result newProject() throws GitAPIException, IOException {
        if( !AccessControl.isGlobalResourceCreatable(UserApp.currentUser()) ){
            return forbidden("'" + UserApp.currentUser().name + "' has no permission");
        }
        Form<Project> filledNewProjectForm = form(Project.class).bindFromRequest();
        String owner = filledNewProjectForm.field("owner").value();
        Organization organization = Organization.findByName(owner);
        User user = User.findByLoginId(owner);

        ValidationResult result = validateForm(filledNewProjectForm, organization, user);
        if (result.hasError()) {
            return result.getResult();
        }

        String gitUrl = filledNewProjectForm.data().get("url");
        Project project = filledNewProjectForm.get();

        if (Organization.isNameExist(owner)) {
            project.organization = organization;
        }
        String errorMessageKey = null;
        try {
            GitRepository.cloneRepository(gitUrl, project);
            Long projectId = Project.create(project);

            if (User.isLoginIdExist(owner)) {
                ProjectUser.assignRole(UserApp.currentUser().id, projectId, RoleType.MANAGER);
            }
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
            List<OrganizationUser> orgUserList = OrganizationUser.findByAdmin(UserApp.currentUser().id);
            filledNewProjectForm.reject("url", errorMessageKey);
            FileUtil.rm_rf(new File(GitRepository.getGitDirectory(project)));
            return badRequest(importing.render("title.newProject", filledNewProjectForm, orgUserList));
        } else {
            return redirect(routes.ProjectApp.project(project.owner, project.name));
        }
    }

    private static ValidationResult validateForm(Form<Project> newProjectForm, Organization organization, User user) {
        boolean hasError = false;
        Result result = null;

        List<OrganizationUser> orgUserList = OrganizationUser.findByAdmin(UserApp.currentUser().id);

        String owner = newProjectForm.field("owner").value();
        String name = newProjectForm.field("name").value();
        boolean ownerIsUser = User.isLoginIdExist(owner);
        boolean ownerIsOrganization = Organization.isNameExist(owner);

        if (!ownerIsUser && !ownerIsOrganization) {
            newProjectForm.reject("owner", "project.owner.invalidate");
            hasError = true;
            result = badRequest(create.render("title.newProject", newProjectForm, orgUserList));
        }

        if (ownerIsUser && UserApp.currentUser().id != user.id) {
            newProjectForm.reject("owner", "project.owner.invalidate");
            hasError = true;
            result = badRequest(create.render("title.newProject", newProjectForm, orgUserList));
        }

        if (ownerIsOrganization && !OrganizationUser.isAdmin(organization.id, UserApp.currentUser().id)) {
            hasError = true;
            result = forbidden(ErrorViews.Forbidden.render("'" + UserApp.currentUser().name + "' has no permission"));
        }

        if (Project.exists(owner, name)) {
            newProjectForm.reject("name", "project.name.duplicate");
            hasError = true;
            result = badRequest(importing.render("title.newProject", newProjectForm, orgUserList));
        }

        String gitUrl = StringUtils.trim(newProjectForm.data().get("url"));
        if (StringUtils.isBlank(gitUrl)) {
            newProjectForm.reject("url", "project.import.error.empty.url");
            hasError = true;
            result = badRequest(importing.render("title.newProject", newProjectForm, orgUserList));
        }

        if (newProjectForm.hasErrors()) {
            newProjectForm.reject("name", "project.name.alert");
            hasError = true;
            result = badRequest(importing.render("title.newProject", newProjectForm, orgUserList));
        }

        return new ValidationResult(result, hasError);
    }
}
