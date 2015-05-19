/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Yi EungJun
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

import com.google.common.collect.Lists;
import info.schleichardt.play2.mailplugin.Mailer;
import notification.INotificationEvent;
import mailbox.EmailAddressWithDetail;
import models.enumeration.EventType;
import models.enumeration.ResourceType;
import models.enumeration.UserState;
import models.resource.Resource;
import notification.MergedNotificationEvent;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.ObjectUtils;
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

import javax.annotation.Nullable;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static models.enumeration.EventType.*;

@Entity
public class NotificationMail extends Model {
    private static final long serialVersionUID = 1L;
    private static final int RECIPIENT_NO_LIMIT = 0;
    static boolean hideAddress = true;
    private static int recipientLimit = RECIPIENT_NO_LIMIT;

    @Id
    public Long id;

    @OneToOne
    public NotificationEvent notificationEvent;

    public static final Finder<Long, NotificationMail> find = new Finder<>(Long.class,
            NotificationMail.class);

    public static void onStart() {
        hideAddress = play.Configuration.root().getBoolean(
            "application.notification.bymail.hideAddress", true);

        recipientLimit = play.Configuration.root().getInt(
                "application.notification.bymail.recipientLimit", RECIPIENT_NO_LIMIT);

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
                        play.Logger.warn("Error occurred while sending notification mails", e);
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
                    Date createdUntil = DateTime.now().minusMillis
                            (MAIL_NOTIFICATION_DELAY_IN_MILLIS).toDate();
                    List<NotificationMail> mails = find.where()
                                    .lt("notificationEvent.created", createdUntil)
                                    .orderBy("notificationEvent.created ASC").findList();

                    List<? extends INotificationEvent> events = extractEventsAndDelete(mails);

                    try {
                        events = mergeEvents(events);
                    } catch (Exception e) {
                        play.Logger.warn("Failed to group events", e);
                    }

                    for (INotificationEvent event : events) {
                        try {
                            if (event.resourceExists()) {
                                sendNotification(event);
                            }
                        } catch (Exception e) {
                            play.Logger.warn("Error occurred while sending a notification mail", e);
                        }
                    }
                }

                /**
                 * Extracts events from the given mails and delete the mails.
                 *
                 * Collects {@link models.NotificationMail#notificationEvent}
                 * field of the given mails, delete the mails and return the
                 * events.
                 *
                 * If an exception occurs while deleting a mail, the event from
                 * the email is not collected and the exception is logged with
                 * a warning message.
                 *
                 * @param mails
                 * @return a list of events
                 */
                private List<INotificationEvent> extractEventsAndDelete(List<NotificationMail> mails) {
                    List<INotificationEvent> events = new ArrayList<>();
                    for (NotificationMail mail : mails) {
                        try {
                            INotificationEvent event = mail.notificationEvent;
                            mail.delete();
                            events.add(event);
                        } catch (Exception e) {
                            Logger.warn("Error occurred while collecting notification events", e);
                        }
                    }
                    return events;
                }
            },
            Akka.system().dispatcher()
        );
    }


    /**
     * Groups events by resource, sender and receivers
     *
     * Each event is one of:
     *
     * a. a notification event
     * b. a merged notification event of a pair: a notification to open or close
     *    an issue or a review thread, and a previous notification to comment on
     *    the resource by the same user
     *
     * @param events orderd by created date
     * @return
     */
    private static List<? extends INotificationEvent> mergeEvents(
            List<? extends INotificationEvent> events) {
        // Hash key to get a notification event by resource and sender
        class EventHashKey {
            private final Resource resource;
            private final User sender;

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                EventHashKey that = (EventHashKey) o;

                if (resource != null ? !resource.equals(that.resource) : that.resource != null) return false;
                if (sender != null ? !sender.equals(that.sender) : that.sender != null) return false;

                return true;
            }

            @Override
            public int hashCode() {
                int result = resource != null ? resource.hashCode() : 0;
                result = 31 * result + (sender != null ? sender.hashCode() : 0);
                return result;
            }

            public EventHashKey(INotificationEvent event) {
                this(event.getResource(), event.getSender());
            }

            public EventHashKey(Resource resource, User sender) {
                this.resource = resource;
                this.sender = sender;
            }
        }

        List<MergedNotificationEvent> result = new LinkedList<>();
        Map<EventHashKey, MergedNotificationEvent> stateChangedEvents = new HashMap<>();

        // Merge events
        for (ListIterator<? extends INotificationEvent> it = events.listIterator(events.size());
             it.hasPrevious();) {
            INotificationEvent event = it.previous();

            if (event.getType().equals(ISSUE_STATE_CHANGED) ||
                    event.getType().equals(REVIEW_THREAD_STATE_CHANGED)) {
                // Collect state-change events
                MergedNotificationEvent stateChangedEvent = new MergedNotificationEvent(event);
                stateChangedEvents.put(new EventHashKey(event), stateChangedEvent);
                result.add(0, stateChangedEvent);
                continue;
            }

            if (event.getType().equals(EventType.NEW_COMMENT) ||
                    event.getType().equals(EventType.NEW_REVIEW_COMMENT)) {
                // If the current event is for commenting then find the matched
                // state-change event and merge the two events.
                EventHashKey key = new EventHashKey(
                        event.getResource().getContainer(),
                        event.getSender());
                MergedNotificationEvent stateChangedEvent = stateChangedEvents.remove(key);

                if (stateChangedEvent != null) {
                    Set<User> stateReceivers = stateChangedEvent.findReceivers();
                    Set<User> commentReceivers = event.findReceivers();

                    if (ObjectUtils.equals(stateReceivers, commentReceivers)) {
                        stateChangedEvent.getMessageSources().add(0, event);
                        // No need to add the current event because it was merged.
                        continue;
                    } else {
                        // If the receivers to state-change and the receivers to
                        // comment differ from each other, split the
                        // notifications into three.

                        Set<User> intersect = new HashSet<>(stateReceivers);
                        intersect.retainAll(commentReceivers);

                        // a. the notification of both of state-change and comment
                        MergedNotificationEvent mergedEvent = new MergedNotificationEvent(
                                stateChangedEvent, Arrays.asList(event, stateChangedEvent));
                        mergedEvent.setReceivers(intersect);
                        result.add(0, mergedEvent);

                        // b. the notification of comment only
                        MergedNotificationEvent commentEvent = new MergedNotificationEvent(event);
                        commentReceivers.removeAll(intersect);
                        commentEvent.setReceivers(commentReceivers);
                        result.add(0, commentEvent);

                        // c. the notification of stage-change only
                        stateReceivers.removeAll(intersect);
                        stateChangedEvent.setReceivers(stateReceivers);
                        continue;
                    }
                }
            }

            result.add(0, new MergedNotificationEvent(event));
        }

        return result;
    }

    /**
     * An email which has Message-ID and/or References header based the given
     * NotificationEvent if possible. The headers help MUA to bind the emails
     * into a thread.
     */
    public static class EventEmail extends HtmlEmail {
        private INotificationEvent event;

        public EventEmail(INotificationEvent event) {
            this.event = event;
        }

        @Override
        protected MimeMessage createMimeMessage(Session aSession) {
            return new MimeMessage(aSession) {
                @Override
                protected void updateMessageID() throws MessagingException {
                    if (event != null && event.getType().isCreating()) {
                        setHeader("Message-ID",
                                event.getResource().getMessageId());
                    } else {
                        super.updateMessageID();
                    }
                }
            };
        }

        public void addReferences() {
            if (event == null || event.getResourceType() == null ||
                    event.getResourceId() == null) {
                return;
            }

            Resource resource = Resource.get(
                    event.getResourceType(), event.getResourceId());

            if (resource == null) {
                return;
            }

            Resource container = resource.getContainer();

            if (container != null) {
                String reference;
                switch (container.getType()) {
                    case COMMENT_THREAD:
                        CommentThread thread =
                                CommentThread.find.byId(Long.valueOf(container.getId()));
                        reference = thread.getFirstReviewComment().asResource().getMessageId();
                        break;
                    default:
                        reference = container.getMessageId();
                        break;
                }
                addHeader("References", reference);
            }
        }
    }

    /**
     * Sends notification mails for the given event.
     *
     * @param event
     * @see <a href="https://github.com/nforge/yobi/blob/master/docs/technical/watch.md>watch.md</a>
     */
    private static void sendNotification(INotificationEvent event) {
        Set<User> receivers = event.findReceivers();

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

        final int partialRecipientSize = getPartialRecipientSize(receivers);

        if (partialRecipientSize <= 0) {
            return;
        }

        HashMap<String, List<User>> usersByLang = new HashMap<>();

        for (User receiver : receivers) {
            String lang = receiver.getPreferredLanguage();

            if (usersByLang.containsKey(lang)) {
                usersByLang.get(lang).add(receiver);
            } else {
                usersByLang.put(lang, new ArrayList<>(Arrays.asList(receiver)));
            }
        }

        for (String langCode : usersByLang.keySet()) {
            List<List<User>> subLists = Lists.partition(usersByLang.get(langCode), partialRecipientSize);

            for (List<User> list : subLists) {
                Set<MailRecipient> toList = getToList(list);
                Set<MailRecipient> bccList = getBccList(list);
                sendMail(event, toList, bccList, langCode);
            }
        }
    }

    private static int getPartialRecipientSize(Set<User> receivers) {
        if (recipientLimit == RECIPIENT_NO_LIMIT) {
            return receivers.size();
        }

        return (hideAddress) ? recipientLimit - getDefaultToList().size() : recipientLimit;
    }

    private static Set<MailRecipient> getToList(List<User> users) {
        return (hideAddress) ? getDefaultToList() : getMailRecipientsFromUsers(users);
    }

    private static Set<MailRecipient> getBccList(List<User> users) {
        return (hideAddress) ? getMailRecipientsFromUsers(users) : new HashSet<MailRecipient>();
    }

    private static Set<MailRecipient> getDefaultToList() {
        Set<MailRecipient> list = new HashSet<>();

        list.add(new MailRecipient(Config.getEmailFromSmtp(), Config.getSiteName()));

        return list;
    }

    private static Set<MailRecipient> getMailRecipientsFromUsers(List<User> users) {
        Set<MailRecipient> list = new HashSet<>();

        for (User user : users) {
            list.add(new MailRecipient(user.email, user.name));
        }

        return list;
    }

    private static void sendMail(INotificationEvent event, Set<MailRecipient> toList, Set<MailRecipient> bccList, String langCode) {
        if (toList.isEmpty()) {
            return;
        }

        final EventEmail email = new EventEmail(event);

        try {
            email.setFrom(Config.getEmailFromSmtp(), event.getSender().name);

            String replyTo = getReplyTo(event.getResource());
            boolean acceptsReply = false;
            if (replyTo != null) {
                email.addReplyTo(replyTo);
                acceptsReply = true;
            }

            for (MailRecipient recipient : toList) {
                email.addTo(recipient.email, recipient.name);
            }

            for (MailRecipient recipient : bccList) {
                email.addBcc(recipient.email, recipient.name);
            }

            // FIXME: gmail은 From과 To에 같은 주소가 있으면 reply-to를 무시한다.

            Lang lang = Lang.apply(langCode);

            String message = event.getMessage(lang);

            if (message == null) {
                return;
            }

            String urlToView = event.getUrlToView();

            email.setSubject(event.getTitle());
            Resource resource = event.getResource();
            if (resource.getType() == ResourceType.ISSUE_COMMENT) {
                IssueComment issueComment = IssueComment.find.byId(Long.valueOf(resource.getId()));
                resource = issueComment.issue.asResource();
            }
            email.setHtmlMsg(getHtmlMessage(lang, message, urlToView, resource, acceptsReply));
            email.setTextMsg(getPlainMessage(lang, message, Url.create(urlToView), acceptsReply));
            email.setCharset("utf-8");
            email.addReferences();
            email.setSentDate(event.getCreatedDate());
            Mailer.send(email);
            String escapedTitle = email.getSubject().replace("\"", "\\\"");
            Set<InternetAddress> recipients = new HashSet<>();
            recipients.addAll(email.getToAddresses());
            recipients.addAll(email.getCcAddresses());
            recipients.addAll(email.getBccAddresses());
            String logEntry = String.format("\"%s\" %s", escapedTitle, recipients);
            play.Logger.of("mail.out").info(logEntry);
        } catch (Exception e) {
            Logger.warn("Failed to send a notification: "
                    + email + "\n" + ExceptionUtils.getStackTrace(e));
        }
    }

    @Nullable
    public static String getReplyTo(Resource resource) {
        if (resource == null) {
            return null;
        }

        if (Config.getEmailFromImap() == null) {
            return null;
        }

        String detail = null;

        switch(resource.getType()) {
            case ISSUE_COMMENT:
            case NONISSUE_COMMENT:
            case COMMIT_COMMENT:
            case REVIEW_COMMENT:
                detail = resource.getContainer().getDetail();
                break;
            case ISSUE_POST:
            case BOARD_POST:
            case COMMIT:
                detail = resource.getDetail();
                break;
            default:
                break;
        }

        if (detail != null) {
            EmailAddressWithDetail addr =
                    new EmailAddressWithDetail(Config.getEmailFromImap());
            addr.setDetail(detail);
            return addr.toString();
        } else {
            return null;
        }
    }

    private static String getHtmlMessage(Lang lang, String message, String urlToView,
                                         Resource resource, boolean acceptsReply) {
        String content = views.html.common.notificationMail.render(
                lang, Markdown.render(message), urlToView, resource, acceptsReply).toString();

        Document doc = Jsoup.parse(content);

        handleLinks(doc);
        handleImages(doc);

        // Do not add "pretty" spaces. Some mail services render a text/plain
        // alternative body in an ugly way. If you are using the such email
        // service, forward or reply this notification email and the recipients
        // read the email in plain text, they will see ugly whitespaces in the
        // footer.
        doc.outputSettings().prettyPrint(false);

        String html = doc.html();

        return html;
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

    private static String getPlainMessage(Lang lang, String message, String urlToView, boolean acceptsReply) {
        String msg = message;
        String url = urlToView;
        String messageKey = acceptsReply ?
                "notification.replyOrLinkToView" : "notification.linkToView";

        if (url != null) {
            msg += String.format("\n\n--\n" + Messages.get(lang, messageKey, url));
        }

        return msg;
    }

}
