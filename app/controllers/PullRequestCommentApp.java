package controllers;

import models.NotificationEvent;
import models.PullRequest;
import models.PullRequestComment;
import models.User;
import models.enumeration.EventType;
import models.enumeration.Operation;
import models.enumeration.ResourceType;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;
import utils.AccessControl;
import utils.Constants;
import utils.ErrorViews;

import java.util.Date;
import java.util.Set;

/**
 * {@link models.PullRequestComment} CRUD 컨트롤러
 *
 */
public class PullRequestCommentApp extends Controller {

    public static Result newComment(String resourceKey) {
        String referer = request().getHeader("Referer");

        Form<PullRequestComment> commentForm = new Form<>(PullRequestComment.class).bindFromRequest();
        if (commentForm.hasErrors()) {
            flash(Constants.WARNING, "post.comment.empty");
            play.Logger.info("Failed to submit a comment: " + commentForm.errors());
            return redirect(referer);
        }

        if (!AccessControl.isCreatable(UserApp.currentUser(), ResourceType.PULL_REQUEST_COMMENT)) {
            return forbidden(ErrorViews.Forbidden.render("error.auth.unauthorized.comment"));
        }


        PullRequestComment newComment = commentForm.get();
        newComment.resourceKey = resourceKey;
        newComment.authorInfos(UserApp.currentUser());
        newComment.save();

        String url = referer + "#comment-" + newComment.id;
        addNewCommentNotification(resourceKey, newComment, url);
        return redirect(url);
    }

    private static void addNewCommentNotification(String resourceKey, PullRequestComment newComment, String url) {
        if(resourceKey.startsWith(ResourceType.PULL_REQUEST.resource())) {
            String prefix = ResourceType.PULL_REQUEST.resource() + Constants.RESOURCE_KEY_DELIM;
            String idString = resourceKey.substring(prefix.length());
            PullRequest pullRequest = PullRequest.findById(Long.parseLong(idString));
            if(pullRequest == null) {
                return;
            }

            String title = NotificationEvent.formatReplyTitle(pullRequest);
            Set<User> watchers = pullRequest.getWatchers();
            addPullRequestCommentNotificationEvent(title, watchers, newComment, url);
        }

    }

    private static void addPullRequestCommentNotificationEvent(String title, Set<User> watchers, PullRequestComment pullRequestComment, String url) {
        watchers.addAll(NotificationEvent.getMentionedUsers(pullRequestComment.contents));
        watchers.remove(User.findByLoginId(pullRequestComment.authorLoginId));

        NotificationEvent notiEvent = new NotificationEvent();
        notiEvent.created = new Date();
        notiEvent.title = title;
        notiEvent.senderId = UserApp.currentUser().id;
        notiEvent.receivers = watchers;
        notiEvent.urlToView = url;
        notiEvent.resourceId = pullRequestComment.id.toString();
        notiEvent.resourceType = pullRequestComment.asResource().getType();
        notiEvent.eventType = EventType.NEW_PULL_REQUEST_COMMENT;
        notiEvent.oldValue = null;
        notiEvent.newValue = pullRequestComment.contents;

        NotificationEvent.add(notiEvent);
    }

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
