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
import play.data.Form;
import play.data.validation.Validation;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;
import utils.Constants;
import utils.ErrorViews;
import views.html.organization.create;
import views.html.organization.view;

import javax.validation.ConstraintViolation;
import java.util.Date;
import java.util.Set;

import static play.data.Form.form;

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

}
