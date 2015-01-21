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

import actions.DefaultProjectCheckAction;
import controllers.annotation.AnonymousCheck;
import models.NotificationEvent;
import models.Project;
import models.ProjectUser;
import models.User;
import models.enumeration.RequestState;
import play.db.ebean.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;

@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
public class EnrollProjectApp extends Controller {

    @Transactional
    @With(DefaultProjectCheckAction.class)
    public static Result enroll(String loginId, String projectName) {
        Project project = Project.findByOwnerAndProjectName(loginId, projectName);

        User user = UserApp.currentUser();
        if (!ProjectUser.isGuest(project, user)) {
            return badRequest();
        }

        if (!User.enrolled(project)) {
            user.enroll(project);
            NotificationEvent.afterMemberRequest(project, user, RequestState.REQUEST);
        }

        return ok();
    }

    @Transactional
    @With(DefaultProjectCheckAction.class)
    public static Result cancelEnroll(String loginId, String proejctName) {
        Project project = Project.findByOwnerAndProjectName(loginId, proejctName);

        User user = UserApp.currentUser();
        if (!ProjectUser.isGuest(project, user)) {
            return badRequest();
        }

        if (User.enrolled(project)) {
            user.cancelEnroll(project);
            NotificationEvent.afterMemberRequest(project, user, RequestState.CANCEL);
        }

        return ok();
    }
}
