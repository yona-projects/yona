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
import controllers.annotation.IsAllowed;
import models.Project;
import models.User;
import models.UserProjectNotification;
import models.Watch;
import models.enumeration.EventType;
import models.enumeration.Operation;
import play.i18n.Messages;
import play.mvc.Controller;
import play.mvc.Result;
import utils.AccessControl;
import utils.ErrorViews;

@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
public class WatchProjectApp extends Controller {

    @IsAllowed(Operation.READ)
    public static Result watch(String userName, String projectName) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        Watch.watch(project.asResource());
        return ok();
    }

    @IsAllowed(Operation.READ)
    public static Result unwatch(String userName, String projectName) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        Watch.unwatch(project.asResource());
        return ok();
    }

    public static Result toggle(Long projectId, String notificationType) {
        EventType notiType = EventType.valueOf(notificationType);
        Project project = Project.find.byId(projectId);
        User user = UserApp.currentUser();

        if(project == null) {
            return notFound(ErrorViews.NotFound.render("error.notfound.project"));
        }
        if(!AccessControl.isAllowed(user, project.asResource(), Operation.READ)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", project));
        }
        if(!Watch.isWatching(user, project.asResource())) {
            return badRequest(Messages.get("error.notfound.watch"));
        }

        UserProjectNotification upn = UserProjectNotification.findOne(user, project, notiType);
        if(upn == null) { // make the EventType OFF, because default is ON.
            UserProjectNotification.unwatchExplictly(user, project, notiType);
        } else {
            upn.toggle();
        }

        return ok();
    }
}
