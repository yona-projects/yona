/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/
package controllers;

import controllers.annotation.AnonymousCheck;
import controllers.annotation.IsAllowed;
import models.Project;
import models.User;
import models.UserProjectNotification;
import models.Watch;
import models.enumeration.EventType;
import models.enumeration.Operation;
import play.db.ebean.Transactional;
import play.i18n.Messages;
import play.mvc.Controller;
import play.mvc.Result;
import utils.AccessControl;
import utils.ErrorViews;

import static models.UserProjectNotification.*;
import static models.enumeration.ResourceType.PROJECT;

@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
public class WatchProjectApp extends Controller {

    @IsAllowed(Operation.READ)
    @Transactional
    public static Result watch(String userName, String projectName) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        Watch.watch(project.asResource());
        return ok();
    }

    @IsAllowed(Operation.READ)
    @Transactional
    public static Result unwatch(String loginId, String projectName) {
        Project project = Project.findByOwnerAndProjectName(loginId, projectName);

        Watch.unwatch(project.asResource());

        UserProjectNotification.deleteUnwatchedProjectNotifications(UserApp.currentUser(), project);
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

        UserProjectNotification userProjectNotification = findOne(user, project, notiType);
        if(userProjectNotification == null) { // not specified yet
            if (isNotifiedByDefault(notiType)) {
                unwatchExplictly(user, project, notiType);
            } else {
                watchExplictly(user, project, notiType);
            }
        } else {
            userProjectNotification.toggle(notiType);
        }

        return ok();
    }
}
