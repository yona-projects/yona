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

import com.sun.mail.imap.IMAPMessage;
import com.googlecode.htmlcompressor.compressor.HtmlCompressor;
import mailbox.exceptions.IssueNotFound;
import mailbox.exceptions.MailHandlerException;
import mailbox.exceptions.PermissionDenied;
import mailbox.exceptions.PostingNotFound;
import models.*;
import models.enumeration.ResourceType;
import models.resource.Resource;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import play.Logger;
import play.api.i18n.Lang;
import play.db.ebean.Transactional;
import play.i18n.Messages;
import utils.AccessControl;
import utils.MimeType;

import javax.annotation.Nonnull;
import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Part;
import javax.mail.internet.*;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * A set of methods to create a resource from an incoming email.
 */
public class CreationViaEmail {
    /**
     * Create a comment from the given email.
     *
     * @param message
     * @param target
     * @throws MessagingException
     * @throws MailHandlerException
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    @Transactional
    public static Comment saveComment(IMAPMessage message, Resource target)
            throws MessagingException, MailHandlerException, IOException, NoSuchAlgorithmException {
        User author = IMAPMessageUtil.extractSender(message);

        if (!AccessControl.isProjectResourceCreatable(
                author, target.getProject(), target.getType())) {
            throw new PermissionDenied(cannotCreateMessage(author,
                    target.getProject(), target.getType()));
        }

        Content parsedMessage = extractContent(message);

        Comment comment = makeNewComment(target, author, parsedMessage.body);

        comment.save();

        Map<String, Attachment> relatedAttachments = saveAttachments(
                parsedMessage.attachments,
                comment.asResource());

        if (new ContentType(parsedMessage.type).match(MimeType.HTML)) {
            comment.contents = postprocessForHTML(comment.contents, relatedAttachments);
            comment.update();
        }

        new OriginalEmail(message.getMessageID(), comment.asResource()).save();

        // Add the event
        addEvent(NotificationEvent.forNewComment(comment, author),
                message.getAllRecipients(), author);

        return comment;
    }

    /**
     * Does postprocessing for HTML document.
     *
     * 1. Replaces cid with attachments.
     * 2. Removes newlines between HTML tags which will make the result rendered
     *    by markdown ugly.
     *
     * @param contents
     * @param relatedAttachments
     * @return
     */
    private static String postprocessForHTML(
            String contents, Map<String, Attachment> relatedAttachments) {
        return new HtmlCompressor().compress(
                replaceCidWithAttachments(contents, relatedAttachments));
    }

    private static Comment makeNewComment(Resource target, User sender, String body) throws IssueNotFound, PostingNotFound {
        Comment comment;
        Long id = Long.valueOf(target.getId());

        switch(target.getType()) {
            case ISSUE_POST:
                Issue issue = Issue.finder.byId(id);
                if (issue == null) {
                    throw new IssueNotFound(id);
                }
                comment = new IssueComment(issue, sender, body);
                break;
            case BOARD_POST:
                Posting posting = Posting.finder.byId(id);
                if (posting == null) {
                    throw new PostingNotFound(id);
                }
                comment = new PostingComment(posting, sender, body);
                break;
            default:
                throw new IllegalArgumentException("Unsupported resource type: " + target.getType());
        }

        return comment;
    }

    /**
     * Create an issue from the given email.
     *
     * @param message
     * @param project
     * @throws MessagingException
     * @throws PermissionDenied
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    static Issue saveIssue(IMAPMessage message,
                           Project project)
            throws MessagingException, PermissionDenied, IOException, NoSuchAlgorithmException {
        User sender = IMAPMessageUtil.extractSender(message);
        if (!AccessControl.isProjectResourceCreatable(
                sender, project, ResourceType.ISSUE_POST)) {
            throw new PermissionDenied(cannotCreateMessage(sender, project,
                    ResourceType.ISSUE_POST));
        }
        Content parsedMessage = extractContent(message);
        String messageId = message.getMessageID();
        Address[] recipients = message.getAllRecipients();
        String subject = message.getSubject();

        return saveIssue(subject, project, sender, parsedMessage, messageId, recipients);
    }

    @Transactional
    public static Issue saveIssue(String subject,
                                     Project project,
                                     User sender,
                                     Content parsedMessage,
                                     String messageId, Address[] recipients)
            throws MessagingException, IOException, NoSuchAlgorithmException {
        Issue issue = new Issue(project, sender, subject, parsedMessage.body);
        issue.save();

        NotificationEvent event = NotificationEvent.forNewIssue(issue, sender);

        Map<String, Attachment> relatedAttachments = saveAttachments(
                parsedMessage.attachments,
                issue.asResource());

        if (new ContentType(parsedMessage.type).match(MimeType.HTML)) {
            issue.body = postprocessForHTML(issue.body, relatedAttachments);
            issue.update();
        }

        new OriginalEmail(messageId, issue.asResource()).save();

        // Add the event
        addEvent(event, recipients, sender);

        return issue;
    }

    /**
     * Create a review comment from the given email.
     *
     * @param message
     * @param target
     * @throws IOException
     * @throws MessagingException
     * @throws PermissionDenied
     * @throws NoSuchAlgorithmException
     */
    static void saveReviewComment(IMAPMessage message,
                                  Resource target)
            throws IOException, MessagingException, PermissionDenied, NoSuchAlgorithmException {
        User sender = IMAPMessageUtil.extractSender(message);

        if (!AccessControl.isProjectResourceCreatable(
                sender, target.getProject(), ResourceType.REVIEW_COMMENT)) {
            throw new PermissionDenied(cannotCreateMessage(sender,
                    target.getProject(), target.getType()));
        }

        Content content = extractContent(message);
        String messageID = message.getMessageID();
        Address[] allRecipients = message.getAllRecipients();

        saveReviewComment(target, sender, content, messageID, allRecipients);
    }

    @Transactional
    protected static ReviewComment saveReviewComment(Resource target,
                                                     User sender,
                                                     Content content,
                                                     String messageID,
                                                     Address[] allRecipients)
            throws MessagingException, IOException, NoSuchAlgorithmException {
        ReviewComment comment;
        CommentThread thread = CommentThread.find.byId(Long.valueOf(target.getId()));

        if (thread == null) {
            throw new IllegalArgumentException();
        }

        comment = new ReviewComment();
        comment.setContents(content.body);
        comment.author = new UserIdent(sender);
        comment.thread = thread;
        comment.save();

        Map<String, Attachment> relatedAttachments = saveAttachments(
                content.attachments,
                comment.asResource());

        if (new ContentType(content.type).match(MimeType.HTML)) {
            // replace cid with attachments
            comment.setContents(replaceCidWithAttachments(
                    comment.getContents(), relatedAttachments));
            comment.update();
        }

        new OriginalEmail(messageID, comment.asResource()).save();

        // Add the event
        if (thread.isOnPullRequest()) {
            addEvent(NotificationEvent.forNewComment(sender, thread.pullRequest, comment),
                    allRecipients, sender);
        } else {
            try {
                String commitId;

                if (thread instanceof CodeCommentThread) {
                    commitId = ((CodeCommentThread)thread).commitId;
                } else if (thread instanceof NonRangedCodeCommentThread) {
                    commitId = ((NonRangedCodeCommentThread)thread).commitId;
                } else {
                    throw new IllegalArgumentException();
                }

                addEvent(NotificationEvent.forNewCommitComment(target.getProject(), comment,
                        commitId, sender), allRecipients, sender);
            } catch (Exception e) {
                Logger.warn("Failed to send a notification", e);
            }
        }

        return comment;
    }

    // You don't need to instantiate this class because this class is just
    // a set of static methods.
    private CreationViaEmail() { }

    @Nonnull
    private static Content extractContent(MimePart part) throws IOException, MessagingException {
        return processPart(part, null);
    }

    @Nonnull
    private static Content processPart(MimePart part,
                                       MimePart parent) throws MessagingException,
            IOException {
        if (part == null) {
            return new Content();
        }

        if (part.getFileName() != null) {
            // Assume that a part which has a filename is an attachment.
            return new Content(part);
        }

        if (part.isMimeType("text/*")) {
            return getContent(part);
        } else if (part.isMimeType("multipart/*")) {
            if (part.isMimeType(MimeType.MULTIPART_RELATED)) {
                return getContentWithAttachments(part);
            } else if (part.isMimeType(MimeType.MULTIPART_ALTERNATIVE)) {
                return getContentOfBestPart(part, parent);
            } else {
                return getJoinedContent(part);
            }
        }

        return new Content();
    }

    private static Content getJoinedContent(MimePart part) throws IOException, MessagingException {
        Content result = new Content();
        MimeMultipart mp = (MimeMultipart) part.getContent();
        for(int i = 0; i < mp.getCount(); i++) {
            MimeBodyPart p = (MimeBodyPart) mp.getBodyPart(i);
            result.merge(processPart(p, part));
        }
        return result;
    }

    private static Content getContent(MimePart part) throws IOException, MessagingException {
        Content result = new Content();
        result.body = (String) part.getContent();
        result.type = part.getContentType();
        return result;
    }

    private static Content getContentOfBestPart(MimePart part, MimePart parent) throws IOException, MessagingException {
        MimeBodyPart best = null;
        MimeMultipart mp = (MimeMultipart) part.getContent();
        for(int i = 0; i < mp.getCount(); i++) {
            // Prefer HTML if the parent is a multipart/related part which may contain
            // inline images, because text/plain cannot embed the images.
            boolean isHtmlPreferred =
                    parent != null && parent.isMimeType(MimeType.MULTIPART_RELATED);
            best = better((MimeBodyPart) mp.getBodyPart(i), best, isHtmlPreferred);
        }
        return processPart(best, part);
    }

    private static Content getContentWithAttachments(MimePart part) throws MessagingException, IOException {
        Content result = new Content();
        String rootId = new ContentType(part.getContentType())
                .getParameter("start");
        MimeMultipart mp = (MimeMultipart) part.getContent();
        for(int i = 0; i < mp.getCount(); i++) {
            MimePart p = (MimePart) mp.getBodyPart(i);
            if (isRootPart(p, i, rootId)) {
                result = result.merge(processPart(p, part));
            } else {
                result.attachments.add(p);
            }
        }
        return result;
    }

    /**
     * Returns true if the given part is root part.
     *
     * The given part is root part, if the part is the first one and the given
     * root id is not defined or the content id of the part equals to the given
     * root id.
     *
     * @param part
     * @param nthPart
     * @param rootId
     * @return
     * @throws MessagingException
     */
    private static boolean isRootPart(MimePart part, int nthPart, String rootId) throws MessagingException {
        return (rootId == null && nthPart == 0) || StringUtils.equals(part.getContentID(), rootId);
    }

    private static int getPoint(BodyPart p, String[] preferences) throws MessagingException {
        if (p == null) {
            return 0;
        }

        for (int i = 0; i < preferences.length; i++) {
            if (p.isMimeType(preferences[i])) {
                return preferences.length + 1 - i;
            }
        }

        return 1;
    }

    /**
     * multipart/related > text/plain > the others
     *
     * @param p
     * @param best
     * @param isHtmlPreferred
     * @return
     * @throws javax.mail.MessagingException
     */
    private static MimeBodyPart better(MimeBodyPart p, MimeBodyPart best, boolean isHtmlPreferred) throws
            MessagingException {
        String[] preferences;
        if (isHtmlPreferred) {
            preferences = new String[]{MimeType.MULTIPART_RELATED, MimeType.HTML, MimeType.PLAIN_TEXT};
        } else {
            preferences = new String[]{MimeType.MULTIPART_RELATED, MimeType.PLAIN_TEXT, MimeType.HTML};
        }

        return getPoint(p, preferences) > getPoint(best, preferences) ? p : best;
    }

    private static String cannotCreateMessage(User user, Project project,
                                             ResourceType resourceType) {
        Lang lang = Lang.apply(user.getPreferredLanguage());
        String resourceTypeName = resourceType.getName(lang);
        return Messages.get(lang, "viaEmail.error.cannotCreate", user, resourceTypeName, project);
    }


    private static void addEvent(NotificationEvent event, Address[] recipients,
                                 User sender) {
        HashSet<User> emailUsers = new HashSet<>();
        emailUsers.add(sender);
        for (Address addr : recipients) {
            emailUsers.add(
                    User.findByEmail(((InternetAddress) addr).getAddress()));
        }
        event.receivers.removeAll(emailUsers);
        NotificationEvent.add(event);
    }

    private static String replaceCidWithAttachments(String html,
                                            Map<String, Attachment> attachments) {
        Document doc = Jsoup.parse(html);
        String[] attrNames = {"src", "href"};

        for (String attrName : attrNames) {
            Elements tags = doc.select("*[" + attrName + "]");
            for (Element tag : tags) {
                String uriString = tag.attr(attrName).trim();

                if (!uriString.toLowerCase().startsWith("cid:")) {
                    continue;
                }

                String cid = uriString.substring("cid:".length());

                if (!attachments.containsKey(cid)) {
                    continue;
                }

                Long id = attachments.get(cid).id;
                tag.attr(attrName,
                         controllers.routes.AttachmentApp.getFile(id).url());
            }
        }

        Elements bodies = doc.getElementsByTag("body");

        if (bodies.size() > 0) {
            return bodies.get(0).html();
        } else {
            return doc.html();
        }
    }

    private static Attachment saveAttachment(Part partToAttach, Resource container)
            throws MessagingException, IOException, NoSuchAlgorithmException {
        Attachment attach = new Attachment();
        String fileName = MimeUtility.decodeText(partToAttach.getFileName());
        attach.store(partToAttach.getInputStream(), fileName, container);
        if (!attach.mimeType.equalsIgnoreCase(partToAttach.getContentType())) {
            Logger.info("The email says the content type is '" + partToAttach
                    .getContentType() + "' but Yobi determines it is '" +
                    attach.mimeType + "'");
        }

        return attach;
    }

    private static Map<String, Attachment> saveAttachments(
            Collection<MimePart> partsToAttach, Resource container)
            throws MessagingException, IOException, NoSuchAlgorithmException {
        Map<String, Attachment> result = new HashMap<>();

        for (MimePart partToAttach : partsToAttach) {
            Attachment attachment = saveAttachment(partToAttach, container);
            if(partToAttach.getContentID() != null) {
                String cid = partToAttach.getContentID().trim();
                cid = cid.replace("<", "");
                cid = cid.replace(">", "");
                result.put(cid, attachment);
            }
        }

        return result;
    }

}
