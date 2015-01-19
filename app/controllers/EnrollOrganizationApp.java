/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @author Changsung Kim
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

import static play.libs.Json.toJson;

import controllers.annotation.AnonymousCheck;
import models.*;
import models.enumeration.RequestState;
import play.db.ebean.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import utils.ValidationResult;

import java.util.HashMap;
import java.util.Map;

@AnonymousCheck
public class EnrollOrganizationApp extends Controller {

    @Transactional
    public static Result enroll(String organizationName) {
        ValidationResult result = validateForEnroll(organizationName);
        if (result.hasError()) {
            return result.getResult();
        }

        Organization organization = Organization.findByName(organizationName);
        if (!User.enrolled(organization)) {
            User user = UserApp.currentUser();
            user.enroll(organization);
            NotificationEvent.afterOrganizationMemberRequest(organization, user, RequestState.REQUEST);
        }

        response().setHeader("Content-Type", "application/json");
        Map<String, String> representationData = new HashMap<String, String>();
        representationData.put("message", "You enrolled in " + organizationName + ".");
        representationData.put("statusMonitorUrl", "http://" + request().host() + routes.OrganizationApp.organization(organizationName).url());
        return status(Http.Status.ACCEPTED, toJson(representationData));
    }

    private static ValidationResult validateForEnroll(String organizationName) {
        Organization organization = Organization.findByName(organizationName);
        if (organization == null) {
            return new ValidationResult(badRequest(), true);
        }

        User user = UserApp.currentUser();
        if (!OrganizationUser.isGuest(organization, user)) {
            return new ValidationResult(badRequest(), true);
        }

        return new ValidationResult(null, false);
    }

    @Transactional
    public static Result cancelEnroll(String organizationName) {
        ValidationResult result = validateForCancelEnroll(organizationName);
        if (result.hasError()) {
            return result.getResult();
        }

        Organization organization = Organization.findByName(organizationName);
        if (User.enrolled(organization)) {
            User user = UserApp.currentUser();
            user.cancelEnroll(organization);
            NotificationEvent.afterOrganizationMemberRequest(organization, user, RequestState.CANCEL);
        }

        response().setHeader("Content-Type", "application/json");
        Map<String, String> representationData = new HashMap<String, String>();
        representationData.put("message", "You canceled to enroll in " + organizationName + ".");
        representationData.put("statusMonitorUrl", "http://" + request().host() + routes.OrganizationApp.organization(organizationName).url());
        return status(Http.Status.ACCEPTED, toJson(representationData));
    }

    private static ValidationResult validateForCancelEnroll(String organizationName) {
        Organization organization = Organization.findByName(organizationName);
        if (organization == null) {
            return new ValidationResult(badRequest(), true);
        }

        User user = UserApp.currentUser();
        if (!OrganizationUser.isGuest(organization, user)) {
            return new ValidationResult(badRequest(), true);
        }

        return new ValidationResult(null, false);
    }
}
