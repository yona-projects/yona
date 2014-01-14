package controllers;

import actions.AnonymousCheckAction;
import controllers.annotation.IsAllowed;
import models.Project;
import models.User;
import models.UserProjectNotification;
import models.enumeration.EventType;
import models.enumeration.Operation;
import play.i18n.Messages;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;
import utils.WatchService;

@With(AnonymousCheckAction.class)
public class WatchProjectApp extends Controller {

    @IsAllowed(Operation.READ)
    public static Result watch(String userName, String projectName) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        WatchService.watch(project.asResource());
        return ok();
    }

    @IsAllowed(Operation.READ)
    public static Result unwatch(String userName, String projectName) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        WatchService.unwatch(project.asResource());
        return ok();
    }

    @IsAllowed(Operation.READ)
    public static Result toggle(Long projectId, String notificationType) {
        EventType notiType = EventType.valueOf(notificationType);
        Project project = Project.find.byId(projectId);
        User user = UserApp.currentUser();

        if(!WatchService.isWatching(user, project.asResource())) {
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
