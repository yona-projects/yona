package controllers;

import info.schleichardt.play2.mailplugin.Mailer;
import models.*;
import models.enumeration.Direction;
import models.enumeration.Operation;
import models.enumeration.ResourceType;
import models.enumeration.UserState;
import models.resource.Resource;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.mail.HtmlEmail;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import play.Logger;
import play.data.Form;
import play.db.ebean.Model;
import play.mvc.Call;
import play.mvc.Content;
import play.mvc.Controller;
import play.mvc.Result;
import utils.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.Set;

/**
 * {@link BoardApp}과 {@link IssueApp}에서 공통으로 사용하는 기능을 담고 있는 컨트롤러 클래스
 */
public class AbstractPostingApp extends Controller {
    public static final int ITEMS_PER_PAGE = 15;

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
        public User getSender();
        public Set<User> getReceivers();
        public String getMessage();
        public String getUrlToView();

    }

    protected static abstract class AbstractNotification implements Notification {
        public String getHtmlMessage() {
            Document doc = Jsoup.parse(Markdown.render(getMessage()));

            String[] attrNames = {"src", "href"};
            for (String attrName : attrNames) {
                Elements tags = doc.select("*[" + attrName + "]");
                for (Element tag : tags) {
                    String uri = tag.attr(attrName);
                    try {
                        if (!new URI(uri).isAbsolute()) {
                            tag.attr(attrName, Url.create(uri));
                        }
                    } catch (URISyntaxException e) {
                        play.Logger.info("A malformed URI is ignored", e);
                    }
                }
            }

            String url = getUrlToView();

            if (url != null) {
                doc.body().append(String.format("<hr><a href=\"%s\">%s</a>", url,
                        "View it on Yobi"));
            }

            return doc.html();
        }

        public String getPlainMessage() {
            String msg = getMessage();
            String url = getUrlToView();

            if (url != null) {
                msg += String.format("\n\n--\nView it on %s", url);
            }

            return msg;
        }
    }

    public static class NotificationFactory {
        public static Notification create(final User sender, final Set<User> receivers, final String title,
                                          final String message, final String urlToView) {
            return new AbstractNotification() {
                public User getSender() {
                    return sender;
                }

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
     *
     * @param comment
     * @param commentForm
     * @param toView
     * @param containerUpdater
     * @return
     * @throws IOException
     */
    public static Result newComment(final Comment comment, Form<? extends Comment> commentForm, final Call toView, Runnable containerUpdater) {
        if (commentForm.hasErrors()) {
            flash(Constants.WARNING, "post.comment.empty");
            return redirect(toView);
        }

        comment.setAuthor(UserApp.currentUser());
        containerUpdater.run(); // this updates comment.issue or comment.posting;
        comment.save();

        // Attach all of the files in the current user's temporary storage.
        Attachment.moveAll(UserApp.currentUser().asResource(), comment.asResource());

        NotificationEvent.afterNewComment(comment, toView.url());

        return redirect(toView + "#comment-" + comment.id);
    }

    /**
     * 어떤 게시물이 등록되었을 때, 그 프로젝트를 지켜보는 사용자들에게 알림 메일을 발송한다.
     *
     * @param noti
     * @see <a href="https://github.com/nforge/yobi/blob/master/docs/technical/watch.md>watch.md</a>
     */
    public static void sendNotification(Notification noti) {
        Set<User> receivers = noti.getReceivers();

        // Remove inactive users.
        Iterator<User> iterator = receivers.iterator();
        while (iterator.hasNext()) {
            User user = iterator.next();
            if (user.state != UserState.ACTIVE) {
                iterator.remove();
            }
        }

        receivers.remove(User.anonymous);

        if(receivers.isEmpty()) {
            return;
        }

        final HtmlEmail email = new HtmlEmail();

        try {
            email.setFrom(Config.getEmailFromSmtp(), noti.getSender().name);
            email.addTo(Config.getEmailFromSmtp(), "Yobi");

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

        // XHR 호출에 의한 경우라면 204 No Content 와 Location 헤더로 응답한다
        if(HttpUtil.isRequestedWithXHR(request())){
            response().setHeader("Location", redirectTo.absoluteURL(request()));
            return status(204);
        }

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
     *
     * @param original
     * @param posting
     * @param postingForm
     * @param redirectTo
     * @param updatePosting
     * @return
     */
    protected static Result editPosting(AbstractPosting original, AbstractPosting posting, Form<? extends AbstractPosting> postingForm, Call redirectTo, Runnable updatePosting) {
        if (postingForm.hasErrors()) {
            return badRequest(ErrorViews.BadRequest.render("error.validation", original.project));
        }

        if (!AccessControl.isAllowed(UserApp.currentUser(), original.asResource(), Operation.UPDATE)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", original.project));
        }

        if (posting.body == null) {
            return status(REQUEST_ENTITY_TOO_LARGE,
                    ErrorViews.RequestTextEntityTooLarge.render());
        }

        posting.id = original.id;
        posting.createdDate = original.createdDate;
        posting.updatedDate = JodaDateUtil.now();
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

}
