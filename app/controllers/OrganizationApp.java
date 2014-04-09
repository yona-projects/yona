/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Keesun Baik
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

import actions.AnonymousCheckAction;
import models.Organization;
import models.User;
import models.enumeration.Operation;
import org.codehaus.jackson.node.ObjectNode;
import play.data.Form;
import play.data.validation.Validation;
import play.db.ebean.Transactional;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;
import utils.AccessControl;
import utils.Constants;
import utils.ErrorViews;
import models.*;
import models.enumeration.RoleType;
import views.html.organization.create;
import views.html.organization.view;
import views.html.organization.setting;

import javax.validation.ConstraintViolation;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Set;

import static play.data.Form.form;
import static utils.LogoUtil.*;

/**
 * @author Keeun Baik
 */
public class OrganizationApp extends Controller {

    @With(AnonymousCheckAction.class)
    public static Result newForm() {
        return ok(create.render("title.newOrganization", new Form<>(Organization.class)));
    }

    @With(AnonymousCheckAction.class)
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

    public static Result organization(String name) {
        Organization org = Organization.findByName(name);
        if(org == null) {
            return notFound(ErrorViews.NotFound.render("error.notfound.organization"));
        }
        return ok(view.render(org));
    }

    private static void validate(Form<Organization> newOrgForm) {
        // 조직 이름 패턴을 검사한다.
        Set<ConstraintViolation<Organization>> results = Validation.getValidator().validate(newOrgForm.get());
        if(!results.isEmpty()) {
            newOrgForm.reject("name", "organization.name.alert");
        }

        String name = newOrgForm.field("name").value();
        // 중복된 loginId로 가입할 수 없다.
        if (User.isLoginIdExist(name)) {
            newOrgForm.reject("name", "organization.name.duplicate");
        }

        // 같은 이름의 조직을 만들 수 없다.
        if (Organization.isNameExist(name)) {
            newOrgForm.reject("name", "organization.name.duplicate");
        }
    }

    /**
     * 그룹에 멤버를 추가한다.
     *
     * @param organizationName
     * @return
     */
    @Transactional
    public static Result addMember(String organizationName) {
        Form<User> addMemberForm = form(User.class).bindFromRequest();
        Result result = validateForAddMember(addMemberForm, organizationName);
        if (result != null) {
            return result;
        }

        User user = User.findByLoginId(addMemberForm.get().loginId);
        Organization organization = Organization.findByOrganizationName(organizationName);
        OrganizationUser.assignRole(user.id, organization.id, RoleType.ORG_MEMBER.roleType());

        return redirect(routes.OrganizationApp.members(organizationName));
    }

    /**
     * {@link #addMember(String)}를 위해 사용되는 변수의 유효성 검사를 한다.
     *
     * @param addMemberForm
     * @param organizationName
     * @return
     */
    private static Result validateForAddMember(Form<User> addMemberForm, String organizationName) {
        String userLoginId = addMemberForm.get().loginId;
        User userToBeAdded = User.findByLoginId(userLoginId);

        if (addMemberForm.hasErrors() || userToBeAdded.isAnonymous()) {
            flash(Constants.WARNING, "organization.member.unknownUser");
            return redirect(routes.OrganizationApp.members(organizationName));
        }

        Organization organization = Organization.findByOrganizationName(organizationName);
        if (organization == null) {
            flash(Constants.WARNING, "organization.member.unknownOrganization");
            return redirect(routes.OrganizationApp.members(organizationName));
        }

        User currentUser = UserApp.currentUser();
        if (!OrganizationUser.isAdmin(organization.id, currentUser.id)) {
            flash(Constants.WARNING, "organization.member.needManagerRole");
            return redirect(routes.OrganizationApp.members(organizationName));
        }

        if (OrganizationUser.exist(organization.id, userToBeAdded.id)) {
            flash(Constants.WARNING, "organization.member.alreadyMember");
            return redirect(routes.OrganizationApp.members(organizationName));
        }

        return null;
    }

    /**
     * 그룹에서 멤버를 삭제한다.
     *
     * @param organizationName
     * @param userId
     * @return
     */
    @Transactional
    public static Result deleteMember(String organizationName, Long userId) {
        Result result = validateForDeleteMember(organizationName, userId);
        if (result != null) {
            return result;
        }

        Organization organization = Organization.findByOrganizationName(organizationName);
        OrganizationUser.delete(organization.id, userId);

        if (UserApp.currentUser().id.equals(userId)) {
            return okWithLocation(routes.OrganizationApp.organization(organizationName).url());
        } else {
            return okWithLocation(routes.OrganizationApp.members(organizationName).url());
        }
    }

    /**
     * {@link #deleteMember(String, Long)}를 위해 사용되는 변수의 유효성 검사를 한다.
     *
     * @param organizationName
     * @param userId
     * @return
     */
    private static Result validateForDeleteMember(String organizationName, Long userId) {
        Organization organization = Organization.findByOrganizationName(organizationName);
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

    /**
     * 그룹 멤버의 권한을 수정한다.
     *
     * @param organizationName
     * @param userId
     * @return
     */
    @Transactional
    public static Result editMember(String organizationName, Long userId) {
        Form<Role> roleForm = form(Role.class).bindFromRequest();
        Result result = validateForEditMember(roleForm, organizationName, userId);
        if (result != null) {
            return result;
        }

        Organization organization = Organization.findByOrganizationName(organizationName);
        OrganizationUser.assignRole(userId, organization.id, roleForm.get().id);

        return status(Http.Status.NO_CONTENT);
    }

    /**
     * {@link #editMember(String, Long)}를 위해 사용되는 변수의 유효성 검사를 한다.
     *
     * @param roleForm
     * @param organizationName
     * @param userId
     * @return
     */
    private static Result validateForEditMember(Form<Role> roleForm, String organizationName, Long userId) {
        if (roleForm.hasErrors()) {
            flash(Constants.WARNING, "organization.member.unknownRole");
            return okWithLocation(routes.OrganizationApp.members(organizationName).url());
        }

        Organization organization = Organization.findByOrganizationName(organizationName);
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
        if (OrganizationUser.isAdmin(organization.id, userId) && organization.getAdmins().size() == 1) {
            flash(Constants.WARNING, "organization.member.atLeastOneAdmin");
            return okWithLocation(routes.OrganizationApp.members(organizationName).url());
        }

        return null;
    }

    /**
     * 그룹 페이지 안에있는 멤버 관리 페이지로 이동한다.
     *
     * @param organizationName
     * @return
     */
    public static Result members(String organizationName) {
        Result result = validateForSetting(organizationName);
        if (result != null) {
            return result;
        }

        Organization organization = Organization.findByOrganizationName(organizationName);

        return ok(views.html.organization.members.render(organization, Role.findOrganizationRoles()));
    }

    /**
     * 그룹 페이지 안에있는 그룹 관리 페이지로 이동한다.
     *
     * @param organizationName
     * @return
     */
    public static Result settingForm(String organizationName) {
        Result result = validateForSetting(organizationName);
        if (result != null) {
            return result;
        }

        Organization organization = Organization.findByOrganizationName(organizationName);

        return ok(views.html.organization.setting.render(organization));
    }

    /**
     * {@link #members(String)}를 위해 사용되는 변수의 유효성 검사를 한다.
     *
     * @param organizationName
     * @return
     */
    private static Result validateForSetting(String organizationName) {
        Organization organization = Organization.findByOrganizationName(organizationName);
        if (organization == null) {
            return notFound(ErrorViews.NotFound.render("organization.member.unknownOrganization", organization));
        }

        User currentUser = UserApp.currentUser();
        if (!OrganizationUser.isAdmin(organization.id, currentUser.id)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", organization));
        }

        return null;
    }

    /**
     * {@code location}을 JSON 형태로 저장하여 ok와 함께 리턴한다.
     *
     * Ajax 요청에 대해 redirect를 리턴하면 정상 작동하지 않음으로 ok에 redirect loation을 포함하여 리턴한다.
     * 클라이언트에서 {@code location}을 확인하여 redirect 시킨다.
     *
     * @param location
     * @return
     */
    private static Result okWithLocation(String location) {
        ObjectNode result = Json.newObject();
        result.put("location", location);

        return ok(result);
    }

    private static Result validateForupdateOrganizationInfo(String organizationName) {
        Result result = validateForSetting(organizationName);

        if (result == null) {
            Form<Organization> organizationForm = form(Organization.class).bindFromRequest();
            if (organizationForm.hasErrors()) {
                Organization organization = Organization.findByOrganizationName(organizationName);
                return badRequest(setting.render(organization));
            }
        }

        return result;
    }

    public static Result updateOrganizationInfo(String organizationName) throws IOException, NoSuchAlgorithmException {
        Result result = validateForupdateOrganizationInfo(organizationName);
        if (result != null) {
            return result;
        }

        Form<Organization> organizationForm = form(Organization.class).bindFromRequest();
        Organization organization = organizationForm.get();
        Http.MultipartFormData body = request().body().asMultipartFormData();
        Http.MultipartFormData.FilePart filePart = body.getFile("logoPath");

        if (!isEmptyFilePart(filePart)) {
            if(!isImageFile(filePart.getFilename())) {
                flash(Constants.WARNING, "project.logo.alert");
                organizationForm.reject("logoPath");
            } else if (filePart.getFile().length() > LOGO_FILE_LIMIT_SIZE) {
                flash(Constants.WARNING, "project.logo.fileSizeAlert");
                organizationForm.reject("logoPath");
            } else {
                Attachment.deleteAll(organization.asResource());
                new Attachment().store(filePart.getFile(), filePart.getFilename(), organization.asResource());
            }
        }

        organization.update();

        return redirect(routes.OrganizationApp.settingForm(organizationName));
    }
}
