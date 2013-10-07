package controllers;

import models.Project;
import models.enumeration.ResourceType;
import models.resource.Resource;
import play.mvc.Result;
import play.mvc.Controller;

public class CommentApp extends Controller {
    public static Result delete(String type, String id) {
        Resource comment = Resource.get(ResourceType.getValue(type), id);

        if (comment.getType().equals(ResourceType.COMMIT_COMMENT)) {
            return CodeHistoryApp.deleteComment(comment.getProject().owner,
                    comment.getProject().name, comment.getContainer().getId(),
                    Long.valueOf(comment.getId()));
        }

        if (comment.getType().equals(ResourceType.PULL_REQUEST_COMMENT)) {
            return PullRequestCommentApp.deleteComment(Long.valueOf(id));
        }

        return badRequest();
    }
}
