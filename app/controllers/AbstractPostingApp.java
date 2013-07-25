package controllers;

import info.schleichardt.play2.mailplugin.Mailer;
import models.enumeration.NotificationType;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import play.Logger;
import play.db.ebean.Model;

import models.resource.Resource;

import models.*;
import models.enumeration.Direction;
import models.enumeration.Operation;
import models.enumeration.ResourceType;

import play.data.Form;
import play.libs.Akka;
import play.mvc.*;
import utils.AccessControl;

import utils.Callback;
import utils.Config;
import utils.Constants;
import utils.ErrorViews;

import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link BoardApp}과 {@link IssueApp}에서 공통으로 사용하는 기능을 담고 있는 컨트롤러 클래스
 */
public class AbstractPostingApp extends Controller {
    public static final int ITEMS_PER_PAGE = 15;

    protected static Set<User> getMentionedUsers(String body) {
        Matcher matcher = Pattern.compile("@" + User.LOGIN_ID_PATTERN).matcher(body);
        Set<User> users = new HashSet<>();
        while(matcher.find()) {
            users.add(User.findByLoginId(matcher.group().substring(1)));
        }
        users.remove(User.anonymous);
        return users;
    }

    /**
     * 검색 조건
     */
    public static class SearchCondition {
        public String orderBy;
        public String orderDir;
        public String filter;
        public int pageNum;

        /**
         * 기본 검색 조건으로 id 역순이며 1페이지를 보여준다.
         */
        public SearchCondition() {
            this.orderDir = Direction.DESC.direction();
            this.orderBy = "id";
            this.filter = "";
            this.pageNum = 1;
        }
    }

    protected static interface Notification {
        public String getTitle();
        public String getHtmlMessage();
        public String getPlainMessage();
        public Set<User> getReceivers();
        public String getMessage();
        public String getUrlToView();
    }

    protected static abstract class AbstractNotification implements Notification {
        public String getHtmlMessage() {
            return String.format(
                    "<pre>%s</pre><hr><a href=\"%s\">%s</a>",
                    getMessage(), getUrlToView(), "View it on Yobi");
        }
        public String getPlainMessage() {
            return String.format(
                    "%s\n\n--\nView it on %s",
                    getMessage(), getUrlToView());
        }
    }

    public static class NotificationFactory {
        public static Notification create(final Set<User> receivers, final String title,
                                          final String message, final String urlToView) {
            return new AbstractNotification() {
                public String getTitle() {
                    return title;
                }

                public Set<User> getReceivers() {
                    return receivers;
                }

                @Override
                public String getMessage() {
                    return message;
                }

                @Override
                public String getUrlToView() {
                    return urlToView;
                }
            };
        }
    }

    /**
     * 새 댓글 저장 핸들러
     *
     * {@code commentForm}에서 입력값을 꺼내 현재 사용자를 작성자로 설정하고 댓글을 저장한다.
     * 현재 사용자 임시 저장소에 있는 첨부파일을 댓글의 첨부파일로 옮긴다.
     *
     * @param comment
     * @param commentForm
     * @param toView
     * @param containerUpdater
     * @return
     * @throws IOException
     */
    public static Result newComment(final Comment comment, Form<? extends Comment> commentForm, final Call toView, Callback containerUpdater) throws IOException {
        if (commentForm.hasErrors()) {
            flash(Constants.WARNING, "board.comment.empty");
            return redirect(toView);
        }

        comment.setAuthor(UserApp.currentUser());
        containerUpdater.run(); // this updates comment.issue or comment.posting;
        comment.save();

        // Attach all of the files in the current user's temporary storage.
        Attachment.moveAll(UserApp.currentUser().asResource(), comment.asResource());

        addNotificationEventFromNewComment(comment, toView);

        return redirect(toView);
    }

    private static void addNotificationEventFromNewComment(Comment comment, Call toView) {
        AbstractPosting post = comment.getParent();
        Set<User> watchers = post.getWatchers();
        watchers.remove(UserApp.currentUser());

        NotificationEvent notiEvent = new NotificationEvent();
        notiEvent.created = new Date();
        notiEvent.title = NotificationEvent.formatReplyTitle(post);;
        notiEvent.senderId = UserApp.currentUser().id;
        notiEvent.receivers = watchers;
        notiEvent.urlToView = toView.absoluteURL(request());
        notiEvent.resourceId = comment.id;
        notiEvent.resourceType = comment.asResource().getType();
        notiEvent.type = NotificationType.NEW_COMMENT;
        notiEvent.oldValue = null;
        notiEvent.newValue = comment.contents;

        NotificationEvent.add(notiEvent);
    }

    /**
     * 어떤 게시물이 등록되었을 때, 그 프로젝트를 지켜보는 사용자들에게 알림 메일을 발송한다.
     *
     * @param noti
     * @see <a href="https://github.com/nforge/yobi/blob/master/docs/technical/watch.md>watch.md</a>
     */
    public static void sendNotification(Notification noti) {
        Set<User> receivers = noti.getReceivers();
        receivers.remove(User.anonymous);
        if(receivers.isEmpty()) {
            return;
        }

        final HtmlEmail email = new HtmlEmail();

        try {
            play.Configuration config = play.Configuration.root();
            email.setFrom(Config.getEmailFromSmtp());
            email.addTo(config.getString("smtp.user") + "@" + config.getString("smtp.domain"));
            for (User receiver : receivers) {
                email.addBcc(receiver.email, receiver.name);
            }
            email.setSubject(noti.getTitle());
            email.setHtmlMsg(noti.getHtmlMessage());
            email.setTextMsg(noti.getPlainMessage());
            email.setCharset("utf-8");
            Mailer.send(email);
            String escapedTitle = email.getSubject().replace("\"", "\\\"");
            String logEntry = String.format("\"%s\" %s", escapedTitle, email.getBccAddresses());
            play.Logger.of("mail").info(logEntry);
        } catch (Exception e) {
            Logger.warn("Failed to send a notification: "
                    + email + "\n" + ExceptionUtils.getStackTrace(e));
        }
    }

    /**
     * {@code target}을 삭제하고 {@code redirectTo}로 이동한다.
     *
     * when: 게시물이나 이슈 또는 그곳에 달린 댓글을 삭제할 때 사용한다.
     *
     * @param target
     * @param resource
     * @param redirectTo
     * @return
     */
    protected static Result delete(Model target, Resource resource, Call redirectTo) {
        if (!AccessControl.isAllowed(UserApp.currentUser(), resource, Operation.DELETE)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", resource.getProject()));
        }

        target.delete();

        return redirect(redirectTo);
    }

    /**
     * {@code posting}에 {@code original} 정보를 채우고 갱신한다.
     *
     * when: 게시물이나 이슈를 수정할 떄 사용한다.
     *
     * 게시물이나 이슈가 수정될 때 {@code noti} 객체가 null이 아니면 알림을 발송한다.
     *
     *
     * @param original
     * @param posting
     * @param postingForm
     * @param redirectTo
     * @param updatePosting
     * @return
     */
    protected static Result editPosting(AbstractPosting original, AbstractPosting posting, Form<? extends AbstractPosting> postingForm, Call redirectTo, Callback updatePosting) {
        if (postingForm.hasErrors()) {
            return badRequest(ErrorViews.BadRequest.render(postingForm.errors().toString(), original.project));
        }

        if (!AccessControl.isAllowed(UserApp.currentUser(), original.asResource(), Operation.UPDATE)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", original.project));
        }

        posting.id = original.id;
        posting.createdDate = original.createdDate;
        posting.authorId = original.authorId;
        posting.authorLoginId = original.authorLoginId;
        posting.authorName = original.authorName;
        posting.project = original.project;
        updatePosting.run();
        posting.update();
        posting.updateProperties();

        // Attach the files in the current user's temporary storage.
        Attachment.moveAll(UserApp.currentUser().asResource(), original.asResource());

        return redirect(redirectTo);
    }

    /**
     * 새 게시물 또는 이슈 생성 권한이 있는지 확인하고 {@code content}를 보여준다.
     *
     * when: 게시물이나 이슈 생성할 때 사용한다.
     *
     * @param project
     * @param resourceType
     * @param content
     * @return
     */
    public static Result newPostingForm(Project project, ResourceType resourceType, Content content) {
        if (!AccessControl.isProjectResourceCreatable(UserApp.currentUser(), project, resourceType)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", project));
        }

        return ok(content);
    }

    /**
     * 현재 사용자가 게시물을 명시적으로 지켜보는 것으로 설정한다.
     *
     * @param target
     * @return
     */
    public static Result watch(AbstractPosting target) {
        User user = UserApp.currentUser();

        if (!AccessControl.isAllowed(user, target.asResource(), Operation.READ)) {
            return forbidden(ErrorViews.Forbidden.render("You have no permission to do that.", target.asResource().getProject()));
        }

        if (user.isAnonymous()) {
            return forbidden(ErrorViews.Forbidden.render("Anonymous cannot watch it.", target.asResource().getProject()));
        }

        target.watch(user);

        return ok();
    }

    /**
     * 현재 사용자가 게시물을 명시적으로 무시하는(지켜보지 않는) 것으로 설정한다.
     *
     * @param target
     * @return
     */
    public static Result unwatch(AbstractPosting target) {
        User user = UserApp.currentUser();

        if (user.isAnonymous()) {
            return forbidden(ErrorViews.Forbidden.render("Anonymous cannot unwatch it.", target.asResource().getProject()));
        }

        target.unwatch(user);

        return ok();
    }
}
