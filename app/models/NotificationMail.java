package models;

import controllers.AbstractPostingApp;
import org.joda.time.DateTime;
import play.Configuration;
import play.db.ebean.Model;
import play.libs.Akka;
import scala.concurrent.duration.Duration;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import java.util.Date;
import java.util.List;
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
                            AbstractPostingApp.sendNotification(
                                    AbstractPostingApp.NotificationFactory.create(
                                            mail.notificationEvent.getSender(),
                                            mail.notificationEvent.receivers,
                                            mail.notificationEvent.title,
                                            mail.notificationEvent.getMessage(),
                                            mail.notificationEvent.urlToView
                                    ));
                        }
                        mail.delete();
                    }
                }
            },
            Akka.system().dispatcher()
        );
    }
}
