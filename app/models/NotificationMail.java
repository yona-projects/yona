package models;

import info.schleichardt.play2.mailplugin.Mailer;
import models.enumeration.UserState;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.mail.HtmlEmail;
import org.joda.time.DateTime;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import play.Configuration;
import play.Logger;
import play.db.ebean.Model;
import play.libs.Akka;
import scala.concurrent.duration.Duration;
import utils.Config;
import utils.Markdown;
import utils.Url;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Entity
public class NotificationMail extends Model {
    private static final long serialVersionUID = 1L;

    @Id
    public Long id;

    @OneToOne
    public NotificationEvent notificationEvent;

    public static Finder<Long, NotificationMail> find = new Finder<>(Long.class,
            NotificationMail.class);

    /**
     * 알림 메일 발송에 대한 스케쥴을 등록한다.
     *
     * 애플리케이션이 시작되고 {@code application.notification.bymail.initdelay}가 경과한 후 부터,
     * {@code application.notification.bymail.interval} 만큼의 시간이 지날 때 마다 알림 메일 발송 작업이 수행된다.
     */
    public static void startSchedule() {
        final Long MAIL_NOTIFICATION_INITDELAY_IN_MILLIS = Configuration.root()
                .getMilliseconds("application.notification.bymail.initdelay", 5 * 1000L);
        final Long MAIL_NOTIFICATION_INTERVAL_IN_MILLIS = Configuration.root()
                .getMilliseconds("application.notification.bymail.interval", 60 * 1000L);
        final int MAIL_NOTIFICATION_DELAY_IN_MILLIS = Configuration.root()
                .getMilliseconds("application.notification.bymail.delay", 180 * 1000L).intValue();

        Akka.system().scheduler().schedule(
            Duration.create(MAIL_NOTIFICATION_INITDELAY_IN_MILLIS, TimeUnit.MILLISECONDS),
            Duration.create(MAIL_NOTIFICATION_INTERVAL_IN_MILLIS, TimeUnit.MILLISECONDS),
            new Runnable() {
                public void run() {
                    try {
                        sendMail();
                    } catch (Exception e) {
                        play.Logger.warn("Failed to send notification mail", e);
                    }
                }

                /**
                 * 알림 메일을 발송한다.
                 *
                 * 등록된지 {@code application.notification.bymail.delay} 이상이 경과한 알림에 대한
                 * 메일들을 모두 가져온 뒤, 알림의 바탕이 되는 resource가 여전히 존재하고 있다면 알림 메일을
                 * 발송한다. 예를 들어, 댓글 등록에 대한 알림이 있다면, 그 댓글이 현재도 존재해야만 알림을
                 * 발송한다.
                 *
                 * 가져온 메일들은 발송 여부와 상관없이 모두 지운다.
                 */
                private void sendMail() {
                    Date sinceDate = DateTime.now().minusMillis
                            (MAIL_NOTIFICATION_DELAY_IN_MILLIS).toDate();
                    List<NotificationMail> mails = find.where()
                                    .lt("notificationEvent.created", sinceDate)
                                    .orderBy("notificationEvent.created ASC").findList();

                    for (NotificationMail mail: mails) {
                        if (mail.notificationEvent.resourceExists()) {
                            sendNotification(mail.notificationEvent);
                        }
                        mail.delete();
                    }
                }
            },
            Akka.system().dispatcher()
        );
    }

    /**
     * {@code event}에 해당하는 알림 이메일을 발송한다.
     *
     * @param event
     * @see <a href="https://github.com/nforge/yobi/blob/master/docs/technical/watch.md>watch.md</a>
     */
    private static void sendNotification(NotificationEvent event) {
        Set<User> receivers = event.receivers;

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
            email.setFrom(Config.getEmailFromSmtp(), event.getSender().name);
            email.addTo(Config.getEmailFromSmtp(), "Yobi");

            for (User receiver : receivers) {
                email.addBcc(receiver.email, receiver.name);
            }

            String message = event.getMessage();
            String urlToView = Url.create(event.urlToView);
            String reference = Url.removeFragment(event.urlToView);

            email.setSubject(event.title);
            email.setHtmlMsg(getHtmlMessage(message, urlToView));
            email.setTextMsg(getPlainMessage(message, urlToView));
            email.setCharset("utf-8");
            email.addHeader("References", "<" + reference + "@" + Config.getHostname() + ">");
            email.setSentDate(event.created);
            Mailer.send(email);
            String escapedTitle = email.getSubject().replace("\"", "\\\"");
            String logEntry = String.format("\"%s\" %s", escapedTitle, email.getBccAddresses());
            play.Logger.of("mail").info(logEntry);
        } catch (Exception e) {
            Logger.warn("Failed to send a notification: "
                    + email + "\n" + ExceptionUtils.getStackTrace(e));
        }
    }

    private static String getHtmlMessage(String message, String urlToView) {
        Document doc = Jsoup.parse(Markdown.render(message));

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

        if (urlToView != null) {
            doc.body().append(String.format("<hr><a href=\"%s\">%s</a>", urlToView,
                    "View it on Yobi"));
        }

        return doc.html();
    }

    private static String getPlainMessage(String message, String urlToView) {
        String msg = message;
        String url = urlToView;

        if (url != null) {
            msg += String.format("\n\n--\nView it on %s", url);
        }

        return msg;
    }

}
