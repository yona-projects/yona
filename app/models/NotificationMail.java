/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Yi EungJun
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import play.api.i18n.Lang;
import play.db.ebean.Model;
import play.i18n.Messages;
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
import java.util.*;
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

        HashMap<String, List<User>> usersByLang = new HashMap<>();

        for (User receiver : receivers) {
            String lang = receiver.lang;

            if (lang == null) {
                lang = Locale.getDefault().getLanguage();
            }

            if (usersByLang.containsKey(lang)) {
                usersByLang.get(lang).add(receiver);
            } else {
                usersByLang.put(lang, new ArrayList<>(Arrays.asList(receiver)));
            }
        }

        for (String langCode : usersByLang.keySet()) {
            final HtmlEmail email = new HtmlEmail();

            try {
                email.setFrom(Config.getEmailFromSmtp(), event.getSender().name);
                email.addTo(Config.getEmailFromSmtp(), utils.Config.getSiteName());

                for (User receiver : usersByLang.get(langCode)) {
                    email.addBcc(receiver.email, receiver.name);
                }

                Lang lang = Lang.apply(langCode);

                String message = event.getMessage(lang);
                String urlToView = Url.create(event.getUrlToView());
                String reference = Url.removeFragment(event.getUrlToView());

                email.setSubject(event.title);
                email.setHtmlMsg(getHtmlMessage(lang, message, urlToView));
                email.setTextMsg(getPlainMessage(lang, message, urlToView));
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
    }

    private static String getHtmlMessage(Lang lang, String message, String urlToView) {
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

        handleImages(doc);

        if (urlToView != null) {
            doc.body().append(String.format("<hr><a href=\"%s\">%s</a>", urlToView,
                    Messages.get(lang, "notification.linkToView", utils.Config.getSiteName())));
        }

        return doc.html();
    }

    private static void handleImages(Document doc){
        for (Element img : doc.select("img")){
            img.attr("style", "max-width:1024px;" + img.attr("style"));
            img.wrap(String.format("<a href=\"%s\" target=\"_blank\" style=\"border:0;outline:0;\"></a>", img.attr("src")));
        }
    }

    private static String getPlainMessage(Lang lang, String message, String urlToView) {
        String msg = message;
        String url = urlToView;

        if (url != null) {
            msg += String.format("\n\n--\n" + Messages.get(lang, "notification.linkToView", url));
        }

        return msg;
    }

}
