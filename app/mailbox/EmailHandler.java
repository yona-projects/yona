/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
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
package mailbox;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import info.schleichardt.play2.mailplugin.Mailer;
import mailbox.exceptions.IllegalDetailException;
import mailbox.exceptions.MailHandlerException;
import models.OriginalEmail;
import models.Project;
import models.Property;
import models.User;
import models.enumeration.Operation;
import models.resource.Resource;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.mail.HtmlEmail;
import org.joda.time.DateTime;
import play.Logger;
import play.api.i18n.Lang;
import play.i18n.Messages;
import utils.AccessControl;
import utils.Config;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A set of methods to process incoming emails.
 *
 * See {@link MailboxService} for more detailed rules to process the emails.
 */
class EmailHandler {
    /**
     * Fetches new emails from the given IMAP folder and process them.
     *
     * @param folder
     * @throws MessagingException
     */
    static void handleNewMessages(IMAPFolder folder) throws MessagingException {
        Long lastUIDValidity = Property.getLong(Property.Name.MAILBOX_LAST_UID_VALIDITY);
        Long lastSeenUID = Property.getLong(Property.Name.MAILBOX_LAST_SEEN_UID);

        long uidValidity = folder.getUIDValidity();

        // Get new messages and handle them
        if (lastUIDValidity != null
                && lastUIDValidity.equals(uidValidity)
                && lastSeenUID != null) {
            // Use the next uid instead of folder.LASTUID which possibly be
            // smaller than lastSeenUID + 1.
            handleMessages(folder, folder.getMessagesByUID(lastSeenUID + 1,
                    folder.getUIDNext()));
        }

        Property.set(Property.Name.MAILBOX_LAST_UID_VALIDITY, uidValidity);
    }

    /**
     * Processes the given emails.
     *
     * @param folder
     * @param messages
     */
    static void handleMessages(IMAPFolder folder, Message[] messages) {
        handleMessages(folder, Arrays.asList(messages));
    }

    private EmailHandler() {
        // You don't need to instantiate this class because this class is just
        // a set of static methods.
    }

    private static List<String> parseMessageIds(String headerValue) {
        // in-reply-to     =   "In-Reply-To:" 1*msg-id CRLF
        // references      =   "References:" 1*msg-id CRLF
        // msg-id          =   [CFWS] "<" id-left "@" id-right ">" [CFWS]
        // CFWS            =   (1*([FWS] comment) [FWS]) / FWS
        // comment         =   "(" *([FWS] ccontent) [FWS] ")"
        // ccontent        =   ctext / quoted-pair / comment
        // FWS             =   ([*WSP CRLF] 1*WSP) /  obs-FWS
        // obs-FWS         =   1*WSP *(CRLF 1*WSP)
        // WSP             =  SP / HTAB
        // ctext           =   %d33-39 /          ; Printable US-ASCII
        //                     %d42-91 /          ;  characters not including
        //                     %d93-126 /         ;  "(", ")", or "\"
        //                     obs-ctext
        List<String> result = new ArrayList<>();
        String cfws = "[^<]*(?:\\([^\\(]*\\))?[^<]*";
        Pattern pattern = Pattern.compile(cfws + "(<[^>]*>)" + cfws);
        Matcher matcher = pattern.matcher(headerValue);
        while(matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

   private static void handleMessages(final IMAPFolder folder, List<Message> messages) {
        // Sort messages by uid; If they are not sorted, it is possible to miss
        // a email as a followed example:
        //
        // 1. Yobi fetches two messages with uid of 1, 3 and 2.
        // 2. Yobi handles a message with uid of 1 and update lastseenuid to 1.
        // 3. Yobi handles a message with uid of 3 and update lastseenuid to 3.
        // 4. **Yobi Shutdown Abnormally**
        // 5. The system administrator restarts Yobi.
        // 6. Yobi fetches messages with uid larger than 3, the value of the
        //    lastseenuid; It means that **the message with uid of 2 will be
        //    never handled!**
        Collections.sort(messages, new Comparator<Message>() {
            @Override
            public int compare(Message m1, Message m2) {
                try {
                    return Long.compare(folder.getUID(m1), folder.getUID(m2));
                } catch (MessagingException e) {
                    play.Logger.warn(
                            "Failed to compare uids of " + m1 + " and " + m2 +
                                    " while sorting messages by the uid; " +
                                    "There is some remote chance of loss of " +
                                    "mail requests.");
                    return 0;
                }
            }
        });

        for (Message msg : messages) {
            handleMessage((IMAPMessage) msg);
        }
    }

    private static void handleMessage(@Nonnull IMAPMessage msg) {
        Exception exception = null;
        long startTime = System.currentTimeMillis();
        User author;

        try {
            // Ignore auto-replied emails to avoid suffering from tons of
            // vacation messages. For more details about auto-replied emails,
            // see https://tools.ietf.org/html/rfc3834
            if (isAutoReplied(msg)) {
                return;
            }
        } catch (MessagingException e) {
            play.Logger.warn(
                    "Failed to determine whether the email is auto-replied or not: " + msg, e);
        }

        try {
            // Ignore the email if there is an email with the same id. It occurs
            // quite frequently because mail servers send an email twice if the
            // email has two addresses differ from each other: e.g.
            // yobi+my/proj@mail.com and yobi+your/proj@mail.com.
            OriginalEmail sameMessage =
                    OriginalEmail.finder.where().eq("messageId", msg.getMessageID()).findUnique();
            if (sameMessage != null) {
                // Warn if the older email was handled one hour or more ago. Because it is
                // quite long time so that possibly the ignored email is actually
                // new one which should be handled.
                if (sameMessage.getHandledDate().before(new DateTime().minusHours(1).toDate())) {
                    String warn = String.format("This email '%s' is ignored because an email with" +
                                    " the same id '%s' was already handled at '%s'",
                            msg, sameMessage.messageId, sameMessage.getHandledDate());
                    play.Logger.warn(warn);
                }
                return;
            }
        } catch (MessagingException e) {
            play.Logger.warn(
                    "Failed to determine whether the email is duplicated or not: " + msg, e);
        }

        InternetAddress[] senderAddresses;

        try {
            senderAddresses = (InternetAddress[]) msg.getFrom();
        } catch (Exception e) {
            play.Logger.error("Failed to get senders from an email", e);
            return;
        }

        if (senderAddresses == null || senderAddresses.length == 0) {
            play.Logger.warn("This email has no sender: " + msg);
            return;
        }

        for (InternetAddress senderAddress : senderAddresses) {
            List<String> errors = new ArrayList<>();

            author = User.findByEmail(senderAddress.getAddress());

            if (author.isAnonymous()) {
                continue;
            }

            try {
                createResources(msg, author, errors);
            } catch (MailHandlerException e) {
                exception = e;
                errors.add(e.getMessage());
            } catch (Exception e) {
                exception = e;
                String shortDescription;
                try {
                    shortDescription = IMAPMessageUtil.asString(msg);
                } catch (MessagingException e1) {
                    shortDescription = msg.toString();
                }
                play.Logger.warn("Failed to process an email: " + shortDescription, e);
                errors.add("Unexpected error occurs");
            }

            if (errors.size() > 0) {
                String username = senderAddress.getPersonal();
                String emailAddress = senderAddress.getAddress();
                String helpMessage = getHelpMessage(
                        Lang.apply(author.getPreferredLanguage()), username, errors);
                reply(msg, username, emailAddress, helpMessage);
            }

            try {
                log(msg, startTime, exception);
            } catch (MessagingException e) {
                play.Logger.warn("Failed to log mail request", e);
            }

            try {
                MailboxService.updateLastSeenUID(msg);
            } catch (MessagingException e) {
                play.Logger.warn("Failed to update the lastSeenUID", e);
            }
        }
    }

    private static class MailHeader {
        private final IMAPMessage message;
        private final String name;

        public MailHeader(@Nonnull IMAPMessage message, @Nonnull String name) {
            this.message = message;
            this.name = name;
        }

        public boolean containsIgnoreCase(@Nonnull String expectedValue) throws MessagingException {
            String[] values = message.getHeader(name);

            if (values == null) {
                return false;
            }

            for (String value : values) {
                int semicolon = value.indexOf(';');
                if (semicolon >= 0) {
                    value = value.substring(0, semicolon);
                }
                if (value.trim().equalsIgnoreCase(expectedValue)) {
                    return true;
                }
            }

            return false;
        }
    }

    /**
     * @param   message
     * @return  true if the given message looks like auto-replied.
     * @throws  MessagingException
     */
    private static boolean isAutoReplied(IMAPMessage message) throws MessagingException {
        return new MailHeader(message, "Auto-Submitted").containsIgnoreCase("auto-replied")
            || new MailHeader(message, "X-Naver-Absent").containsIgnoreCase("yes");
    }

    private static void createResources(IMAPMessage msg, User sender, List<String> errors)
            throws MessagingException, IOException, MailHandlerException, NoSuchAlgorithmException {

        // Find all threads by message-ids in In-Reply-To and References headers in the email.
        Set<Resource> threads = getThreads(msg);

        // Note: It is possible that an email creates many resources.
        for (Project project : getProjects(msg, sender, errors)) {
            boolean hasCommented = false;

            // If there is a related thread, we assume the author wants to comment the thread.
            for (Resource thread : threads) {
                if (thread.getProject().id.equals(project.id)) {
                    switch(thread.getType()) {
                        case COMMENT_THREAD:
                            CreationViaEmail.saveReviewComment(msg, thread);
                            break;
                        case ISSUE_POST:
                        case BOARD_POST:
                            CreationViaEmail.saveComment(msg, thread);
                            break;
                    }
                    hasCommented = true;
                }
            }

            // If there is no related thread, we assume the author wants to create new issue.
            if (!hasCommented) {
                CreationViaEmail.saveIssue(msg, project);
            }
        }
    }

    private static Set<Project> getProjects(IMAPMessage msg, User sender, List<String> errors) throws MessagingException {
        Set<Project> projects = new HashSet<>();
        for (EmailAddressWithDetail address : getMailAddressesToYobi(msg.getAllRecipients())) {
            Project project;
            String detail = address.getDetail();

            if (StringUtils.isEmpty(detail)) {
                // TODO: Do we need send help message?
                continue;
            }

            Lang lang = Lang.apply(sender.getPreferredLanguage());

            if (StringUtils.equalsIgnoreCase(detail, "help")) {
                reply(msg, sender, getHelpMessage(lang, sender));
                continue;
            }

            try {
                project = getProjectFromDetail(detail);
            } catch (IllegalDetailException e) {
                errors.add(Messages.get(lang, "viaEmail.error.email", address.toString()));
                continue;
            }

            if (project == null ||
                    !AccessControl.isAllowed(sender, project.asResource(), Operation.READ)) {
                errors.add(Messages.get(lang, "viaEmail.error.forbidden.or.notfound",
                        address.toString()));
                continue;
            }

            projects.add(project);
        }
        return projects;
    }

    /**
     * Returns the threads to which the given message is sent.
     *
     * The threads are determined by message-ids in In-Reply-To and References
     * headers and the detail part of the recipients' email addresses.
     *
     * @param msg
     * @return
     * @throws MessagingException
     */
    private static Set<Resource> getThreads(IMAPMessage msg) throws MessagingException {
        // Get message-ids from In-Reply-To and References headers.
        Set<String> messageIds = new HashSet<>();

        String inReplyTo = msg.getInReplyTo();
        if (inReplyTo != null) {
            messageIds.addAll(parseMessageIds(inReplyTo));
        }

        for (String references : ArrayUtils.nullToEmpty(msg.getHeader("References"))) {
            if (references != null) {
                messageIds.addAll(parseMessageIds(references));
            }
        }

        // Find threads by the message-id.
        Set<Resource> threads = new HashSet<>();

        for (String messageId : messageIds) {
            for (Resource resource : findResourcesByMessageId(messageId)) {
                switch (resource.getType()) {
                    case COMMENT_THREAD:
                    case ISSUE_POST:
                    case BOARD_POST:
                        threads.add(resource);
                        break;
                    case REVIEW_COMMENT:
                        threads.add(resource.getContainer());
                        break;
                    default:
                        Logger.info(
                                "Cannot comment a resource of unknown type: " + resource);
                        break;
                }
            }
        }

        for (EmailAddressWithDetail address : getMailAddressesToYobi(msg.getAllRecipients())) {
            Resource thread = getResourceFromDetail(address.getDetail());
            if (thread != null) {
                threads.add(thread);
            }
        }

        return threads;
    }

    private static @Nullable Resource getResourceFromDetail(@Nullable String detail) {
        if (detail == null) {
            return null;
        }

        // detail = <owner>/<project>/<path>
        // path = <resource-type>/<resource-id>
        String[] segments = detail.split("/", 3);

        if (segments.length < 3) {
            return null;
        }

        return Resource.findByPath(segments[2]);
    }

    private static Project getProjectFromDetail(String detail) throws IllegalDetailException {
        String[] segments = detail.split("/");

        if (segments.length < 2) {
            throw new IllegalDetailException();
        }

        return Project.findByOwnerAndProjectName(segments[0], segments[1]);
    }

    private static String getHelpMessage(Lang lang, String username, List<String> errors) {
        String help = "";
        String paragraphSeparator = "\n\n";
        String sampleProject = "dlab/hive";
        EmailAddressWithDetail address = new EmailAddressWithDetail(Config.getEmailFromImap());
        address.setDetail("dlab/hive/issue");
        help += Messages.get(lang, "viaEmail.help.hello", username);
        if (errors != null && errors.size() > 0) {
            help += paragraphSeparator;
            String error;
            String messageKey;
            if (errors.size() > 1) {
                error = "\n* " + StringUtils.join(errors, "\n* ");
                messageKey = "viaEmail.help.errorMultiLine";
            } else {
                error = errors.get(0);
                messageKey = "viaEmail.help.errorSingleLine";
            }
            help += Messages.get(lang, messageKey, Config.getSiteName(), error);
        }
        help += paragraphSeparator;
        help += Messages.get(lang, "viaEmail.help.intro", Config.getSiteName());
        help += paragraphSeparator;
        help += Messages.get(lang, "viaEmail.help.description", sampleProject, address);
        help += paragraphSeparator;
        help += Messages.get(lang, "viaEmail.help.bye", Config.getSiteName());
        return help;
    }

    private static String getHelpMessage(Lang lang, User to) {
        return getHelpMessage(lang, to, null);
    }

    private static String getHelpMessage(Lang lang, User to, List<String> errors) {
        return getHelpMessage(lang, to.name, errors);
    }

    private static void reply(IMAPMessage origin, String username, String emailAddress,
                              String msg) {
        final HtmlEmail email = new HtmlEmail();

        try {
            email.setFrom(Config.getEmailFromSmtp(), Config.getSiteName());
            email.addTo(emailAddress, username);
            String subject;
            if (!origin.getSubject().toLowerCase().startsWith("re:")) {
                subject = "Re: " + origin.getSubject();
            } else {
                subject = origin.getSubject();
            }
            email.setSubject(subject);
            email.setTextMsg(msg);
            email.setCharset("utf-8");
            email.setSentDate(new Date());
            email.addHeader("In-Reply-To", origin.getMessageID());
            email.addHeader("References", origin.getMessageID());
            Mailer.send(email);
            String escapedTitle = email.getSubject().replace("\"", "\\\"");
            String logEntry = String.format("\"%s\" %s", escapedTitle, email.getToAddresses());
            play.Logger.of("mail").info(logEntry);
        } catch (Exception e) {
            Logger.warn("Failed to send an email: "
                    + email + "\n" + ExceptionUtils.getStackTrace(e));
        }
    }

    private static void reply(IMAPMessage origin, User to, String msg) {
        reply(origin, to.name, to.email, msg);
    }

    private static Set<EmailAddressWithDetail> getMailAddressesToYobi(Address[] addresses) {
        Set<EmailAddressWithDetail> addressesToYobi = new HashSet<>();

        if (addresses != null) {
            for (Address recipient : addresses) {
                EmailAddressWithDetail address = new EmailAddressWithDetail(((InternetAddress) recipient).getAddress());
                if (address.isToYobi()) {
                    addressesToYobi.add(address);
                }
            }
        }

        return addressesToYobi;
    }

    /**
     * Log a message for an E-mail request.
     *
     * @param message    An email
     * @param startTime  the time in milliseconds when the request is received
     */
    private static void log(@Nonnull IMAPMessage message, long startTime,
                            Exception exception) throws MessagingException {

        String time = ((Long) startTime != null) ?
                ((System.currentTimeMillis() - startTime) + "ms") : "-";

        String entry = String.format("%s %s %s",
                IMAPMessageUtil.asString(message),
                exception == null ? "SUCCESS" : "FAILED",
                time);

        if (exception != null &&
                !(exception instanceof MailHandlerException)) {
            Logger.of("mail.in").error(entry, exception);
        } else {
            Logger.of("mail.in").info(entry);
        }
    }

    /**
     * Finds resources by the given message id.
     *
     * If there are resources created via an email which matches the message
     * id, returns the resources, else finds and returns resources which matches
     * the resource path taken from the id-left part of the message id.
     *
     * The format of message-id is defined by RFC 5322 as follows:
     *
     *     msg-id  =   [CFWS] "<" id-left "@" id-right ">" [CFWS]
     *
     * @param  messageId
     * @return the set of resources
     */
    @Nonnull
    public static Set<Resource> findResourcesByMessageId(String messageId) {
        Set<Resource> resources = new HashSet<>();
        Set<OriginalEmail> originalEmails = OriginalEmail.finder.where().eq
                ("messageId", messageId).findSet();

        if (originalEmails.size() > 0) {
            for (OriginalEmail originalEmail : originalEmails) {
                resources.add(Resource.get(originalEmail.resourceType, originalEmail.resourceId));
            }
            return resources;
        }

        try {
            String resourcePath = IMAPMessageUtil.getIdLeftFromMessageId(messageId);
            Resource resource = Resource.findByPath(resourcePath);
            if (resource != null) {
                resources.add(resource);
            }
        } catch (Exception e) {
            Logger.info(
                    "Error while finding a resource by message-id '" + messageId + "'", e);
        }

        return resources;
    }
}
