/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Keesun Baik
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package controllers;

import controllers.annotation.AnonymousCheck;
import models.*;
import models.enumeration.RoleType;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import play.data.Form;
import io.ebean.annotation.Transactional;
import play.i18n.Messages;
import utils.LegacyController;
import play.mvc.Result;
import playRepository.GitRepository;
import utils.*;
import views.html.project.create;
import views.html.project.importing;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static utils.FormUtil.form;

@AnonymousCheck
public class ImportApp extends LegacyController {

    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    public Result importForm() {
        Form<Project> projectForm = form(Project.class).bindFromRequest(request(), "owner");
        projectForm = projectForm.discardingErrors();
        List<OrganizationUser> orgUserList = OrganizationUser.findByAdmin(UserApp.currentUser().id);
        return ok(importing.render("title.newProject", projectForm, orgUserList));
    }

    @Transactional
    public Result newProject() throws Exception {
        if( !AccessControl.isGlobalResourceCreatable(UserApp.currentUser()) ){
            return forbidden("'" + UserApp.currentUser().name + "' has no permission");
        }
        Form<Project> filledNewProjectForm = form(Project.class).bindFromRequest(request());
        String owner = filledNewProjectForm.field("owner").value().orElse("");
        Organization organization = Organization.findByName(owner);
        User user = User.findByLoginId(owner);

        ValidationResult result = validateForm(filledNewProjectForm, organization, user);
        if (result.hasError()) {
            return result.getResult();
        }

        String gitUrl = filledNewProjectForm.rawData().get("url");
        Project project = filledNewProjectForm.get();

        if (Organization.isNameExist(owner)) {
            project.organization = organization;
        }

        String authId = filledNewProjectForm.field("authId").value().orElse("");
        String authPw = filledNewProjectForm.field("authPw").value().orElse("");
        boolean hasNoCredentials = StringUtils.isEmpty(authId) && StringUtils.isEmpty(authPw);

        try {
            if(hasNoCredentials){
                GitRepository.cloneRepository(gitUrl, project);
            } else {
                GitRepository.cloneRepository(gitUrl, project, authId, authPw);
            }

            Long projectId = Project.create(project);

            saveProjectMenuSetting(project);

            if (User.isLoginIdExist(owner)) {
                ProjectUser.assignRole(UserApp.currentUser().id, projectId, RoleType.MANAGER);
            }
        } catch (InvalidRemoteException e) {
            // It is not an url.
            utils.FormUtil.reject(filledNewProjectForm, "url", "project.import.error.wrong.url");
        } catch (JGitInternalException e) {
            // The url seems that does not locate a git repository.
            utils.FormUtil.reject(filledNewProjectForm, "url", "project.import.error.wrong.url");
        } catch (TransportException e) {
            addDetailedTransportErrorMessage(filledNewProjectForm, e, hasNoCredentials);
        }

        if (!filledNewProjectForm.errors().isEmpty()) {
            List<OrganizationUser> orgUserList = OrganizationUser.findByAdmin(UserApp.currentUser().id);
            FileUtil.rm_rf(GitRepository.getGitDirectory(project));
            return badRequest(importing.render("title.newProject", filledNewProjectForm, orgUserList));
        } else {
            return redirect(routes.ProjectApp.project(project.owner, project.name));
        }
    }

    private static void saveProjectMenuSetting(Project project) {
        Form<ProjectMenuSetting> filledUpdatedProjectMenuSettingForm = form(ProjectMenuSetting.class).bindFromRequest(request());
        ProjectMenuSetting updatedProjectMenuSetting = filledUpdatedProjectMenuSettingForm.get();

        project.refresh();
        updatedProjectMenuSetting.project = project;

        if (project.menuSetting == null) {
            updatedProjectMenuSetting.save();
        } else {
            updatedProjectMenuSetting.id = project.menuSetting.id;
            updatedProjectMenuSetting.update();
        }
    }

    /**
     * Add assorted error messages from TransportException like Unauthorized(401), Forbidden(403)
     * or other transport error with HTTP response code to the given form.
     *
     * Referenced TransportHttp.java of jGit which throws TransportException while connect to remote repository.
     *
     * @see https://github.com/eclipse/jgit/blob/4cb0bd8/org.eclipse.jgit/src/org/eclipse/jgit/transport/TransportHttp.java#L491
     * @param filledNewProjectForm
     * @param e
     * @param hasNoCredentials
     * @return
     */
    private static void addDetailedTransportErrorMessage(Form<Project> filledNewProjectForm, TransportException e, boolean hasNoCredentials){
        String errorMessage = e.getMessage();

        // HttpConnection.HTTP_UNAUTHORIZED : 401
        if(errorMessage.contains(JGitText.get().notAuthorized)){
            if(hasNoCredentials){
                utils.FormUtil.reject(filledNewProjectForm, "repoAuth", "required");
                utils.FormUtil.reject(filledNewProjectForm, "url", "project.import.error.transport.unauthorized");
            } else {
                utils.FormUtil.reject(filledNewProjectForm, "authId", "project.import.error.transport.failedToAuth");
            }
        } else if(errorMessage.contains(java.text.MessageFormat.format(JGitText.get().serviceNotPermitted, ""))){
            // HttpConnection.HTTP_FORBIDDEN : 403
            utils.FormUtil.reject(filledNewProjectForm, "url", "project.import.error.transport.forbidden");
        } else {
            // and for other errors
            String statusCode = errorMessage.split(" ")[1]; // 0 = URL, 1 = ResponseCode, 2 = ResponseMessage
            utils.FormUtil.reject(filledNewProjectForm, "url", MessagesUtil.get("project.import.error.transport", statusCode));
        }
    }

    private static ValidationResult validateForm(Form<Project> newProjectForm, Organization organization, User user) {
        boolean hasError = false;
        Result result = null;

        List<OrganizationUser> orgUserList = OrganizationUser.findByAdmin(UserApp.currentUser().id);

        String owner = newProjectForm.field("owner").value().orElse("");
        String name = newProjectForm.field("name").value().orElse("");
        boolean ownerIsUser = User.isLoginIdExist(owner);
        boolean ownerIsOrganization = Organization.isNameExist(owner);

        if (!ownerIsUser && !ownerIsOrganization) {
            utils.FormUtil.reject(newProjectForm, "owner", "project.owner.invalidate");
            hasError = true;
            result = badRequest(create.render("title.newProject", newProjectForm, orgUserList));
        }

        if (ownerIsUser && !Objects.equals(UserApp.currentUser().id, user.id)) {
            utils.FormUtil.reject(newProjectForm, "owner", "project.owner.invalidate");
            hasError = true;
            result = badRequest(create.render("title.newProject", newProjectForm, orgUserList));
        }

        if (ownerIsOrganization && !OrganizationUser.isAdmin(organization.id, UserApp.currentUser().id)) {
            hasError = true;
            result = forbidden(ErrorViews.Forbidden.render("'" + UserApp.currentUser().name + "' has no permission"));
        }

        if (Project.exists(owner, name)) {
            utils.FormUtil.reject(newProjectForm, "name", "project.name.duplicate");
            hasError = true;
            result = badRequest(importing.render("title.newProject", newProjectForm, orgUserList));
        }

        String gitUrl = StringUtils.trim(newProjectForm.rawData().get("url"));
        if (StringUtils.isBlank(gitUrl)) {
            utils.FormUtil.reject(newProjectForm, "url", "project.import.error.empty.url");
            hasError = true;
            result = badRequest(importing.render("title.newProject", newProjectForm, orgUserList));
        }

        if (newProjectForm.hasErrors()) {
            utils.FormUtil.reject(newProjectForm, "name", "project.name.alert");
            hasError = true;
            result = badRequest(importing.render("title.newProject", newProjectForm, orgUserList));
        }

        return new ValidationResult(result, hasError);
    }
}
