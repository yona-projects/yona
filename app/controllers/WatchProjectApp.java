package controllers;

import models.Project;
import models.User;
import models.UserProjectNotification;
import models.enumeration.EventType;
import models.enumeration.Operation;
import play.i18n.Messages;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;
import utils.AccessControl;
import utils.ErrorViews;
import utils.WatchService;
import actions.AnonymousCheckAction;
import controllers.annotation.ProjectAccess;

public class WatchProjectApp extends Controller {

    @ProjectAccess(Operation.READ)
    @With(AnonymousCheckAction.class)
    public static Result watch(String userName, String projectName) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        WatchService.watch(project.asResource());
        return redirect(request().getHeader(Http.HeaderNames.REFERER));
    }

    @ProjectAccess(Operation.READ)
    @With(AnonymousCheckAction.class)
    public static Result unwatch(String userName, String projectName) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        WatchService.unwatch(project.asResource());
        return redirect(request().getHeader(Http.HeaderNames.REFERER));
    }

    @With(AnonymousCheckAction.class)
    public static Result toggle(Long projectId, String notificationType) {
        EventType notiType = EventType.valueOf(notificationType);
        Project project = Project.find.byId(projectId);
        User user = UserApp.currentUser();

        if(project == null) {
            return notFound(ErrorViews.NotFound.render("No project matches given projectId '" + projectId + "'"));
        }
        if(!AccessControl.isAllowed(user, project.asResource(), Operation.READ)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", project));
        }
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
