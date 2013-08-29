package controllers;

import models.NotificationEvent;
import models.PullRequest;
import models.SimpleComment;
import models.User;
import models.enumeration.NotificationType;
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
 * {@link models.SimpleComment} CRUD 컨트롤러
 *
 */
public class SimpleCommentApp extends Controller {

    public static Result newComment(String resourceKey) {
        String referer = request().getHeader("Referer");

        Form<SimpleComment> commentForm = new Form<>(SimpleComment.class).bindFromRequest();
        if (commentForm.hasErrors()) {
            flash(Constants.WARNING, "board.comment.empty");
            return redirect(referer);
        }

        if (!AccessControl.isCreatable(UserApp.currentUser(), ResourceType.SIMPLE_COMMENT)) {
            return forbidden(ErrorViews.Forbidden.render("auth.unauthorized.comment"));
        }


        SimpleComment newComment = commentForm.get();
        newComment.resourceKey = resourceKey;
        newComment.authorInfos(UserApp.currentUser());
        newComment.save();

        String url = referer + "#comment-" + newComment.id;
        addNewCommentNotification(resourceKey, newComment, url);
        return redirect(url);
    }

    private static void addNewCommentNotification(String resourceKey, SimpleComment newComment, String url) {
        if(resourceKey.startsWith(ResourceType.PULL_REQUEST.resource())) {
            String prefix = ResourceType.PULL_REQUEST.resource() + Constants.RESOURCE_KEY_DELIM;
            String idString = resourceKey.substring(prefix.length());
            PullRequest pullRequest = PullRequest.findById(Long.parseLong(idString));
            if(pullRequest == null) {
                return;
            }

            String title = NotificationEvent.formatReplyTitle(pullRequest);
            Set<User> watchers = pullRequest.getWatchers();
            addSimpleCommentNotificationEvent(title, watchers, newComment, url);
        }

    }

    private static void addSimpleCommentNotificationEvent(String title, Set<User> watchers, SimpleComment simpleComment, String url) {
        watchers.addAll(NotificationEvent.getMentionedUsers(simpleComment.contents));
        watchers.remove(User.findByLoginId(simpleComment.authorLoginId));

        NotificationEvent notiEvent = new NotificationEvent();
        notiEvent.created = new Date();
        notiEvent.title = title;
        notiEvent.senderId = UserApp.currentUser().id;
        notiEvent.receivers = watchers;
        notiEvent.urlToView = url;
        notiEvent.resourceId = simpleComment.id.toString();
        notiEvent.resourceType = simpleComment.asResource().getType();
        notiEvent.notificationType = NotificationType.NEW_SIMPLE_COMMENT;
        notiEvent.oldValue = null;
        notiEvent.newValue = simpleComment.contents;

        NotificationEvent.add(notiEvent);
    }

    public static Result deleteComment(Long id) {
        SimpleComment simpleComment = SimpleComment.findById(id);
        if(simpleComment == null) {
            notFound();
        }
        if (!AccessControl.isAllowed(UserApp.currentUser(), simpleComment.asResource(), Operation.DELETE)) {
            return forbidden(ErrorViews.Forbidden.render("auth.unauthorized.waringMessage"));
        }
        simpleComment.delete();
        String backPageUrl = request().getHeader("Referer");
        return redirect(backPageUrl);
    }

}
