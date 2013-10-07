package controllers;

import models.enumeration.Operation;
import models.resource.Resource;
import play.mvc.Result;
import play.mvc.Controller;
import utils.AccessControl;

import static models.enumeration.ResourceType.*;

public class CommentApp extends Controller {
    public static Result delete(String type, String id) {
        Resource comment = Resource.get(getValue(type), id);

        if (comment == null) {
            return badRequest();
        }

        if (!AccessControl.isAllowed(UserApp.currentUser(), comment, Operation.DELETE)) {
            return forbidden();
        }

        switch(comment.getType()) {
            case COMMIT_COMMENT:
            case PULL_REQUEST_COMMENT:
                comment.delete();
                return ok();
            default:
                return badRequest();
        }
    }
}
