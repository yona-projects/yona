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
import models.resource.Resource;
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
import utils.RouteUtil;
import utils.Url;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
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
    static boolean hideAddress = true;

    @Id
    public Long id;

    @OneToOne
    public NotificationEvent notificationEvent;

    public static Finder<Long, NotificationMail> find = new Finder<>(Long.class,
            NotificationMail.class);

    public static void onStart() {
        hideAddress = play.Configuration.root().getBoolean(
            "application.notification.bymail.hideAddress", true);

        if (notificationEnabled()) {
            NotificationMail.startSchedule();
        }
    }

    private static boolean notificationEnabled() {
        play.Configuration config = play.Configuration.root();
        Boolean notificationEnabled = config.getBoolean("notification.bymail.enabled");
        return notificationEnabled == null || notificationEnabled;
    }

    /**
     * Sets up a schedule to send notification mails.
     *
     * Since the application started and then
     * {@code application.notification.bymail.initdelay} of time passed, send
     * notification mails every {@code application.notification.bymail.interval}
     * of time.
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
                 * Sends notification mails.
                 *
                 * Get and send notification mails for the events which satisfy
                 * all of following conditions:
                 * - {@code application.notification.bymail.delay} of time
                 *   passed since the event is created.
                 * - The base resource still exists. In the case of an event
                 *   for new comment, the comment still exists.
                 *
                 * Every mail will be deleted regardless of whether it is sent
                 * or not.
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
     * An email which has Message-ID and/or References header based the given
     * NotificationEvent if possible. The headers help MUA to bind the emails
     * into a thread.
     */
    public static class EventEmail extends HtmlEmail {
        private NotificationEvent event;

        public EventEmail(NotificationEvent event) {
            this.event = event;
        }

        @Override
        protected MimeMessage createMimeMessage(Session aSession) {
            return new MimeMessage(aSession) {
                @Override
                protected void updateMessageID() throws MessagingException {
                    if (event != null && event.eventType.isCreating()) {
                        setHeader("Message-ID",
                                String.format("<%s@%s>",
                                        event.getUrlToView(),
                                        Config.getHostname()));
                    } else {
                        super.updateMessageID();
                    }
                }
            };
        }

        public void addReferences() {
            if (event == null || event.resourceType == null ||
                    event.resourceId == null) {
                return;
            }

            Resource resource = Resource.get(
                    event.resourceType, event.resourceId);

            if (resource == null) {
                return;
            }

            Resource container = resource.getContainer();

            if (container != null) {
                String reference = RouteUtil.getUrl(
                        container.getType(), container.getId());
                addHeader("References",
                        "<" + reference + "@" + Config.getHostname() + ">");
            }
        }
    }

    /**
     * Sends notification mails for the given event.
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
            final EventEmail email = new EventEmail(event);

            try {
                if (hideAddress) {
                    email.setFrom(Config.getEmailFromSmtp(), event.getSender().name);
                    email.addTo(Config.getEmailFromSmtp(), utils.Config.getSiteName());
                } else {
                    email.setFrom(event.getSender().email, event.getSender().name);
                }

                for (User receiver : usersByLang.get(langCode)) {
                    if (hideAddress) {
                        email.addBcc(receiver.email, receiver.name);
                    } else {
                        email.addTo(receiver.email, receiver.name);
                    }
                }

                if (email.getToAddresses().isEmpty()) {
                    continue;
                }

                Lang lang = Lang.apply(langCode);

                String message = event.getMessage(lang);
                String urlToView = event.getUrlToView();
                String reference = Url.removeFragment(event.getUrlToView());

                email.setSubject(event.title);
                email.setHtmlMsg(getHtmlMessage(lang, message, urlToView, event.getResource()));
                email.setTextMsg(getPlainMessage(lang, message, Url.create(urlToView)));
                email.setCharset("utf-8");
                email.addReferences();
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

    private static String getHtmlMessage(Lang lang, String message, String urlToView, Resource resource) {
        String content = getRenderedHTMLWithTemplate(lang, Markdown.render(message), urlToView, resource);
        Document doc = Jsoup.parse(content);

        handleLinks(doc);
        handleImages(doc);

        return doc.html();
    }

    private static String getRenderedHTMLWithTemplate(Lang lang, String message, String urlToView, Resource resource){
        return views.html.common.notificationMail.render(lang, message, urlToView, resource).toString();
    }

    /**
     * Make every link to be absolute and to have 'rel=noreferrer' if
     * necessary.
     */
    public static void handleLinks(Document doc){
        String hostname = Config.getHostname();
        String[] attrNames = {"src", "href"};
        Boolean noreferrer =
            play.Configuration.root().getBoolean("application.noreferrer", false);

        for (String attrName : attrNames) {
            Elements tags = doc.select("*[" + attrName + "]");
            for (Element tag : tags) {
                boolean isNoreferrerRequired = false;
                String uriString = tag.attr(attrName);

                if (noreferrer && attrName.equals("href")) {
                    isNoreferrerRequired = true;
                }

                try {
                    URI uri = new URI(uriString);

                    if (!uri.isAbsolute()) {
                        tag.attr(attrName, Url.create(uriString));
                    }

                    if (uri.getHost() == null || uri.getHost().equals(hostname)) {
                        isNoreferrerRequired = false;
                    }
                } catch (URISyntaxException e) {
                    play.Logger.info("A malformed URI is detected while" +
                            " checking an email to send", e);
                }

                if (isNoreferrerRequired) {
                    tag.attr("rel", tag.attr("rel") + " noreferrer");
                }
            }
        }
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
