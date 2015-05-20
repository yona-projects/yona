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
import models.enumeration.Operation;
import models.enumeration.RequestState;
import models.enumeration.RoleType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.data.Form;
import play.data.validation.Validation;
import play.db.ebean.Transactional;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import utils.*;
import views.html.organization.create;
import views.html.organization.deleteForm;
import views.html.organization.members;
import views.html.organization.setting;
import views.html.organization.view;

import javax.servlet.ServletException;
import javax.validation.ConstraintViolation;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static play.data.Form.form;
import static utils.LogoUtil.*;

/**
 * @author Keeun Baik
 */
@AnonymousCheck
public class OrganizationApp extends Controller {
    /**
     * show New Group page
     * @return {@link Result}
     */
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    public static Result newForm() {
        return ok(create.render("title.newOrganization", new Form<>(Organization.class)));
    }

    /**
     * create New Group
     * @return {@link Result}
     * @throws Exception
     */
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    public static Result newOrganization() throws Exception {
        Form<Organization> newOrgForm = form(Organization.class).bindFromRequest();
        validate(newOrgForm);
        if (newOrgForm.hasErrors()) {
            flash(Constants.WARNING, newOrgForm.error("name").message());
            return badRequest(create.render("title.newOrganization", newOrgForm));
        } else {
            Organization org = newOrgForm.get();
            org.created = new Date();
            org.save();

            UserApp.currentUser().createOrganization(org);
            return redirect(routes.OrganizationApp.organization(org.name));
        }
    }

    private static void validate(Form<Organization> newOrgForm) {
        Set<ConstraintViolation<Organization>> results = Validation.getValidator().validate(newOrgForm.get());
        if (!results.isEmpty()) {
            newOrgForm.reject("name", "organization.name.alert");
        }

        String name = newOrgForm.field("name").value();
        if (User.isLoginIdExist(name)) {
            newOrgForm.reject("name", "organization.name.duplicate");
        }

        if (Organization.isNameExist(name)) {
            newOrgForm.reject("name", "organization.name.duplicate");
        }
    }

    /**
     * show specific group's main page
     * @param organizationName group name
     * @return {@link Result}
     */
    public static Result organization(String organizationName) {
        Organization org = Organization.findByName(organizationName);
        if (org == null) {
            return notFound(ErrorViews.NotFound.render("error.notfound.organization"));
        }
        return ok(view.render(org));
    }

    @Transactional
    public static Result addMember(String organizationName) {
        Form<User> addMemberForm = form(User.class).bindFromRequest();
        Result result = validateForAddMember(addMemberForm, organizationName);
        if (result != null) {
            return result;
        }

        User user = User.findByLoginId(addMemberForm.get().loginId);
        Organization organization = Organization.findByName(organizationName);
        OrganizationUser.assignRole(user.id, organization.id, RoleType.ORG_MEMBER.roleType());
        organization.cleanEnrolledUsers();
        NotificationEvent.afterOrganizationMemberRequest(organization, user, RequestState.ACCEPT);

        return redirect(routes.OrganizationApp.members(organizationName));
    }

    private static Result validateForAddMember(Form<User> addMemberForm, String organizationName) {
        String userLoginId = addMemberForm.get().loginId;
        User userToBeAdded = User.findByLoginId(userLoginId);

        if (addMemberForm.hasErrors() || userToBeAdded.isAnonymous()) {
            flash(Constants.WARNING, "organization.member.unknownUser");
            return redirect(routes.OrganizationApp.members(organizationName));
        }

        Organization organization = Organization.findByName(organizationName);
        if (organization == null) {
            flash(Constants.WARNING, "organization.member.unknownOrganization");
            return redirect(routes.OrganizationApp.members(organizationName));
        }

        User currentUser = UserApp.currentUser();
        if (!AccessControl.isAllowed(currentUser, organization.asResource(), Operation.UPDATE)) {
            flash(Constants.WARNING, "organization.member.needManagerRole");
            return redirect(routes.OrganizationApp.members(organizationName));
        }

        if (OrganizationUser.exist(organization.id, userToBeAdded.id)) {
            flash(Constants.WARNING, "organization.member.alreadyMember");
            return redirect(routes.OrganizationApp.members(organizationName));
        }

        return null;
    }

    @Transactional
    public static Result deleteMember(String organizationName, Long userId) {
        Result result = validateForDeleteMember(organizationName, userId);
        if (result != null) {
            return result;
        }

        Organization organization = Organization.findByName(organizationName);
        OrganizationUser.delete(organization.id, userId);

        if (UserApp.currentUser().id.equals(userId)) {
            return okWithLocation(routes.OrganizationApp.organization(organizationName).url());
        } else {
            return okWithLocation(routes.OrganizationApp.members(organizationName).url());
        }
    }

    private static Result validateForDeleteMember(String organizationName, Long userId) {
        Organization organization = Organization.findByName(organizationName);
        if (organization == null) {
            return notFound(ErrorViews.NotFound.render("organization.member.unknownOrganization", organization));
        }

        if (!OrganizationUser.exist(organization.id, userId)) {
            flash(Constants.WARNING, "organization.member.isNotAMember");
            return okWithLocation(routes.OrganizationApp.members(organizationName).url());
        }

        User currentUser = UserApp.currentUser();
        if (!AccessControl.isAllowed(currentUser, organization.asResource(), Operation.UPDATE)
                && !currentUser.id.equals(userId)) {
            flash(Constants.WARNING, "organization.member.needManagerRole");
            return okWithLocation(routes.OrganizationApp.members(organizationName).url());
        }

        if (OrganizationUser.isAdmin(organization.id, userId) && organization.getAdmins().size() == 1) {
            flash(Constants.WARNING, "organization.member.atLeastOneAdmin");
            return okWithLocation(routes.OrganizationApp.members(organizationName).url());
        }

        return null;
    }

    @Transactional
    public static Result editMember(String organizationName, Long userId) {
        Form<Role> roleForm = form(Role.class).bindFromRequest();
        Result result = validateForEditMember(roleForm, organizationName, userId);
        if (result != null) {
            return result;
        }

        Organization organization = Organization.findByName(organizationName);
        OrganizationUser.assignRole(userId, organization.id, roleForm.get().id);

        return status(Http.Status.NO_CONTENT);
    }

    private static Result validateForEditMember(Form<Role> roleForm, String organizationName, Long userId) {
        if (roleForm.hasErrors()) {
            flash(Constants.WARNING, "organization.member.unknownRole");
            return okWithLocation(routes.OrganizationApp.members(organizationName).url());
        }

        Organization organization = Organization.findByName(organizationName);
        if (organization == null) {
            return notFound(ErrorViews.NotFound.render("organization.member.unknownOrganization", organization));
        }

        if (!OrganizationUser.exist(organization.id, userId)) {
            flash(Constants.WARNING, "organization.member.isNotAMember");
            return okWithLocation(routes.OrganizationApp.members(organizationName).url());
        }

        User currentUser = UserApp.currentUser();
        if (!AccessControl.isAllowed(currentUser, organization.asResource(), Operation.UPDATE)) {
            flash(Constants.WARNING, "organization.member.needManagerRole");
            return okWithLocation(routes.OrganizationApp.members(organizationName).url());
        }

        if (organization.isLastAdmin(User.find.byId(userId))
                && roleForm.get().id.equals(RoleType.ORG_MEMBER.roleType())) {
            flash(Constants.WARNING, "organization.member.atLeastOneAdmin");
            return okWithLocation(routes.OrganizationApp.members(organizationName).url());
        }

        return null;
    }

    @Transactional
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    public static Result leave(String organizationName) {
        ValidationResult result = validateForLeave(organizationName);

        if (!result.hasError()) {
            OrganizationUser.delete(Organization.findByName(organizationName).id, UserApp.currentUser().id);
        }

        return result.getResult();
    }

    public static ValidationResult validateForLeave(String organizationName) {
        Organization organization = Organization.findByName(organizationName);

        if (organization == null) {
            return new ValidationResult(notFound(getJsonErrorMsg("organization.member.unknownOrganization")), true);
        }

        if (!AccessControl.isAllowed(UserApp.currentUser(), organization.asResource(), Operation.LEAVE)) {
            if (OrganizationUser.findAdminsOf(organization).size() == 1) {
                return new ValidationResult(forbidden(getJsonErrorMsg("organization.member.atLeastOneAdmin")), true);
            }
        }

        return new ValidationResult(okWithLocation(routes.OrganizationApp.organization(organizationName).url()), false);
    }

    private static JsonNode getJsonErrorMsg(String errMsg) {
        Map<String, String> response = new HashMap<>();
        response.put("errorMsg", errMsg);
        return Json.toJson(response);
    }

    public static Result members(String organizationName) {
        Result result = validateForSetting(organizationName);
        if (result != null) {
            return result;
        }

        Organization organization = Organization.findByName(organizationName);

        return ok(members.render(organization, Role.findOrganizationRoles()));
    }

    private static Result validateForSetting(String organizationName) {
        Organization organization = Organization.findByName(organizationName);
        if (organization == null) {
            return notFound(ErrorViews.NotFound.render("organization.member.unknownOrganization", organization));
        }

        User currentUser = UserApp.currentUser();
        if (!AccessControl.isAllowed(currentUser, organization.asResource(), Operation.UPDATE)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", organization));
        }

        return null;
    }

    public static Result settingForm(String organizationName) {
        Result result = validateForSetting(organizationName);
        if (result != null) {
            return result;
        }

        Organization organization = Organization.findByName(organizationName);

        return ok(setting.render(organization, form(Organization.class).fill(organization)));
    }

    private static Result okWithLocation(String location) {
        ObjectNode result = Json.newObject();
        result.put("location", location);

        return ok(result);
    }

    /**
     * update group's info
     * @param organizationName group name
     * @return {@link Result}
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    public static Result updateOrganizationInfo(String organizationName) throws IOException, NoSuchAlgorithmException, ServletException {
        Form<Organization> organizationForm = form(Organization.class).bindFromRequest();
        Organization modifiedOrganization = organizationForm.get();

        Result result = validateForUpdate(organizationForm, modifiedOrganization);
        if (result != null) {
            return result;
        }

        Http.MultipartFormData.FilePart filePart = request().body().asMultipartFormData()
                .getFile("logoPath");
        if (!isEmptyFilePart(filePart)) {
            Attachment.deleteAll(modifiedOrganization.asResource());
            new Attachment().store(filePart.getFile(), filePart.getFilename(), modifiedOrganization.asResource());
        }

        Organization original = Organization.find.byId(modifiedOrganization.id);
        original.updateWith(modifiedOrganization);

        return redirect(routes.OrganizationApp.settingForm(modifiedOrganization.name));
    }

    private static Result validateForUpdate(Form<Organization> organizationForm, Organization modifiedOrganization) {
        Organization organization = Organization.find.byId(modifiedOrganization.id);
        if (organization == null) {
            return notFound(ErrorViews.NotFound.render("organization.member.unknownOrganization"));
        }

        if (!AccessControl.isAllowed(UserApp.currentUser(), organization.asResource(), Operation.UPDATE)) {
            flash(Constants.WARNING, "organization.member.needManagerRole");
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", organization));
        }

        if (isDuplicateName(organization, modifiedOrganization)) {
            organizationForm.reject("name", "organization.name.duplicate");
            return badRequest(setting.render(organization, organizationForm));
        }

        Http.MultipartFormData.FilePart filePart = request().body().asMultipartFormData()
                .getFile("logoPath");
        if (!isEmptyFilePart(filePart)) {
            if (!isImageFile(filePart.getFilename())) {
                flash(Constants.WARNING, "project.logo.alert");
                organizationForm.reject("logoPath");
            }
            if (filePart.getFile().length() > LOGO_FILE_LIMIT_SIZE) {
                flash(Constants.WARNING, "project.logo.fileSizeAlert");
                organizationForm.reject("logoPath");
            }
        }

        if (organizationForm.hasErrors()) {
            return badRequest(setting.render(organization, organizationForm));
        }

        return null;
    }

    private static boolean isDuplicateName(Organization organization, Organization modifiedOrganization) {
        if (isNotChangedName(organization.name, modifiedOrganization.name)) {
            return false;
        }
        if (User.isLoginIdExist(modifiedOrganization.name)) {
            return true;
        }
        if (Organization.isNameExist(modifiedOrganization.name)) {
            return true;
        }
        return false;
    }

    private static boolean isNotChangedName(String name, String modifiedName) {
        return name.equals(modifiedName);
    }

    public static Result deleteForm(String organizationName) {
        Result result = validateForSetting(organizationName);
        if (result != null) {
            return result;
        }

        Organization organization = Organization.findByName(organizationName);

        return ok(deleteForm.render(organization));
    }

    @Transactional
    public static Result deleteOrganization(String organizationName) {
        Organization organization = Organization.findByName(organizationName);

        ValidationResult result = validateForDelete(organization);

        if (result.hasError()) {
            return result.getResult();
        }

        organization.delete();

        return redirect(routes.Application.index());
    }

    private static ValidationResult validateForDelete(Organization organization) {
        if (organization == null) {
            return new ValidationResult(notFound(getJsonErrorMsg("organization.member.unknownOrganization")), true);
        }
        if (!AccessControl.isAllowed(UserApp.currentUser(), organization.asResource(), Operation.DELETE)) {
            return new ValidationResult(notFound(getJsonErrorMsg("organization.member.needManagerRole")), true);
        }
        if (organization.projects != null && organization.projects.size() > 0) {
            return new ValidationResult(notFound(getJsonErrorMsg("organization.delete.impossible.project.exist")), true);
        }

        return new ValidationResult(okWithLocation(routes.OrganizationApp.organization(organization.name).url()), false);
    }
}
