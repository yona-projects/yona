package controllers;

import models.Attachment;
import models.NotificationEvent;
import models.PullRequest;
import models.PullRequestComment;
import models.enumeration.Operation;
import play.data.Form;
import play.db.ebean.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import utils.AccessControl;
import utils.Constants;
import utils.ErrorViews;

import java.io.IOException;
import java.net.URL;

/**
 * {@link models.PullRequestComment} CRUD 컨트롤러
 *
 */
public class PullRequestCommentApp extends Controller {

    @Transactional
    public static Result newComment(String ownerName, String projectName, Long pullRequestId) throws IOException {
        PullRequest pullRequest = PullRequest.findById(pullRequestId);

        if (pullRequest == null) {
            return notFound();
        }

        String referer = request().getHeader("Referer");

        Form<PullRequestComment> commentForm = new Form<>(PullRequestComment.class).bindFromRequest();
        if (commentForm.hasErrors()) {
            flash(Constants.WARNING, "post.comment.empty");
            play.Logger.info("Failed to submit a comment: " + commentForm.errors());
            return redirect(referer);
        }

        if (!AccessControl.isCreatable(UserApp.currentUser())) {
            return forbidden(ErrorViews.Forbidden.render("error.auth.unauthorized.comment"));
        }

        PullRequestComment newComment = commentForm.get();
        newComment.authorInfos(UserApp.currentUser());
        newComment.pullRequest = pullRequest;
        newComment.save();

        // Attach all of the files in the current user's temporary storage to this comment.
        Attachment.moveAll(UserApp.currentUser().asResource(), newComment.asResource());

        String url = new URL(referer).getPath() + "#comment-" + newComment.id;
        NotificationEvent.afterNewComment(UserApp.currentUser(), pullRequest, newComment, url);
        return redirect(url);
    }

    @Transactional
    public static Result deleteComment(Long id) {
        PullRequestComment pullRequestComment = PullRequestComment.findById(id);
        if(pullRequestComment == null) {
            notFound();
        }
        if (!AccessControl.isAllowed(UserApp.currentUser(), pullRequestComment.asResource(), Operation.DELETE)) {
            return forbidden(ErrorViews.Forbidden.render("error.auth.unauthorized.waringMessage"));
        }
        pullRequestComment.delete();
        String backPageUrl = request().getHeader("Referer");
        return redirect(backPageUrl);
    }

}
