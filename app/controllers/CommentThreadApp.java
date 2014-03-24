package controllers;

import models.CommentThread;
import models.enumeration.Operation;
import play.db.ebean.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import utils.AccessControl;

import static models.CommentThread.ThreadState.CLOSED;
import static models.CommentThread.ThreadState.OPEN;

public class CommentThreadApp extends Controller {

    @Transactional
    public static Result updateState(Long id, CommentThread.ThreadState state) {
        CommentThread thread = CommentThread.find.byId(id);

        if (thread == null) {
            return notFound();
        }

        Operation operation;

        switch(state) {
            case OPEN:
                operation = Operation.REOPEN;
                break;
            case CLOSED:
                operation = Operation.CLOSE;
                break;
            default:
                throw new UnsupportedOperationException();
        }

        if (!AccessControl.isAllowed(UserApp.currentUser(), thread.asResource(), operation)) {
            return forbidden();
        }

        thread.state = state;
        thread.update();

        return ok();
    }

    public static Result open(Long id) {
        return updateState(id, OPEN);
    }

    public static Result close(Long id) {
        return updateState(id, CLOSED);
    }
}
