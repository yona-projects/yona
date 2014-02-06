package controllers;

import models.CommentThread;
import models.enumeration.Operation;
import models.resource.Resource;
import play.db.ebean.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import utils.AccessControl;

import static models.enumeration.ResourceType.getValue;

public class CommentThreadApp extends Controller {

    @Transactional
    public static Result updateState(Long id, CommentThread.ThreadState state) {
        CommentThread thread = CommentThread.find.byId(id);

        if (thread == null) {
            return notFound();
        }

        if (!AccessControl.isAllowed(UserApp.currentUser(), thread.asResource(), Operation.REOPEN)) {
            return forbidden();
        }

        thread.state = state;
        thread.update();

        return ok();
    }

    public static Result open(Long id) {
        return updateState(id, CommentThread.ThreadState.OPEN);
    }

    public static Result close(Long id) {
        return updateState(id, CommentThread.ThreadState.CLOSED);
    }
}
