/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/
package models;

import com.avaje.ebean.RawSqlBuilder;
import controllers.UserApp;
import controllers.routes;
import notification.INotificationEvent;
import models.enumeration.*;
import models.resource.GlobalResource;
import models.resource.Resource;
import models.resource.ResourceConvertible;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.joda.time.DateTime;
import org.tmatesoft.svn.core.SVNException;
import play.api.i18n.Lang;
import play.db.ebean.Model;
import play.i18n.Messages;
import play.libs.Akka;
import playRepository.*;
import scala.concurrent.duration.Duration;
import utils.AccessControl;
import utils.DiffUtil;
import utils.EventConstants;
import utils.RouteUtil;

import javax.naming.LimitExceededException;
import javax.persistence.*;
import javax.servlet.ServletException;
import java.beans.Transient;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static models.UserProjectNotification.findEventUnwatchersByEventType;
import static models.UserProjectNotification.findEventWatchersByEventType;
import static models.Watch.findUnwatchers;
import static models.Watch.findWatchers;
import static models.enumeration.EventType.*;

@Entity
public class NotificationEvent extends Model implements INotificationEvent {
    private static final long serialVersionUID = 1L;

    @Id
    public Long id;

    public static final Finder<Long, NotificationEvent> find = new Finder<>(Long.class, NotificationEvent.class);

    public String title;

    public Long senderId;

    @ManyToMany(cascade = CascadeType.ALL)
    public Set<User> receivers;

    @Temporal(TemporalType.TIMESTAMP)
    public Date created;

    @Enumerated(EnumType.STRING)
    public ResourceType resourceType;

    public String resourceId;

    @Enumerated(EnumType.STRING)
    public EventType eventType;

    @Lob @Basic(fetch=FetchType.EAGER)
    public String oldValue;

    @Lob @Basic(fetch=FetchType.EAGER)
    public String newValue;

    @OneToOne(mappedBy="notificationEvent", cascade = CascadeType.ALL)
    public NotificationMail notificationMail;

    /**
     * Returns receivers.
     *
     * This is much faster than field access to {@link #receivers}.
     *
     * @return receivers
     */
    public Set<User> findReceivers() {
        String sql = "select n4user.id from n4user where id in (select n4user_id " +
                     "from notification_event_n4user where " +
                     "notification_event_id = '" + id + "')";

        return User.find.setRawSql(RawSqlBuilder.parse(sql).create()).findSet();
    }

    @Override
    public void setReceivers(Set<User> receivers) {
        throw new UnsupportedOperationException();
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    @Transient
    public String getMessage() {
        return getMessage(Lang.defaultLang());
    }

    @Transient
    public String getMessage(Lang lang) {
        switch (eventType) {
            case ISSUE_STATE_CHANGED:
                if (newValue.equals(State.CLOSED.state())) {
                    return Messages.get(lang, "notification.issue.closed");
                } else {
                    return Messages.get(lang, "notification.issue.reopened");
                }
            case ISSUE_ASSIGNEE_CHANGED:
                if (newValue == null) {
                    return Messages.get(lang, "notification.issue.unassigned");
                } else {
                    return Messages.get(lang, "notification.issue.assigned", newValue);
                }
            case ISSUE_MILESTONE_CHANGED:
                if (Milestone.findById(Long.parseLong(newValue)) == null) {
                    return Messages.get(lang, "notification.milestone.changed", Messages.get(Lang.defaultLang(), "issue.noMilestone"));
                } else {
                    return Messages.get(lang, "notification.milestone.changed", Milestone.findById(Long.parseLong(newValue)).title);
                }
            case NEW_ISSUE:
            case NEW_POSTING:
            case NEW_PULL_REQUEST:
            case NEW_COMMIT:
            case COMMENT_UPDATED:
                return newValue;
            case NEW_COMMENT:
                return newValue + oldValue;
            case ISSUE_BODY_CHANGED:
            case POSTING_BODY_CHANGED:
                return DiffUtil.getDiffText(oldValue, newValue);
            case NEW_REVIEW_COMMENT:
                try {
                    ReviewComment reviewComment = ReviewComment.find.byId(Long.valueOf(this.resourceId));
                    if (reviewComment != null) {
                        return buildCommentedCodeMessage(reviewComment, lang);
                    }
                } catch (Exception e) {
                    play.Logger.error(
                            "Failed to generate a notification " +
                            "message for a review comment", e);
                }

                return newValue;
            case PULL_REQUEST_STATE_CHANGED:
                if (State.OPEN.state().equals(newValue)) {
                    return Messages.get(lang, "notification.pullrequest.reopened");
                } else {
                    return Messages.get(lang, "notification.pullrequest." + newValue);
                }
            case PULL_REQUEST_COMMIT_CHANGED:
                return newValue;
            case PULL_REQUEST_MERGED:
                return Messages.get(lang, "notification.type.pullrequest.merged." + newValue) + "\n" + StringUtils.defaultString(oldValue, StringUtils.EMPTY);
            case MEMBER_ENROLL_REQUEST:
                if (RequestState.REQUEST.name().equals(newValue)) {
                    return Messages.get(lang, "notification.member.enroll.request");
                } else  if (RequestState.ACCEPT.name().equals(newValue)) {
                    return Messages.get(lang, "notification.member.enroll.accept");
                } else {
                    return Messages.get(lang, "notification.member.enroll.cancel");
                }
            case ORGANIZATION_MEMBER_ENROLL_REQUEST:
                if (RequestState.REQUEST.name().equals(newValue)) {
                    return Messages.get(lang, "notification.organization.member.enroll.request");
                } else  if (RequestState.ACCEPT.name().equals(newValue)) {
                    return Messages.get(lang, "notification.organization.member.enroll.accept");
                } else {
                    return Messages.get(lang, "notification.organization.member.enroll.cancel");
                }
            case PULL_REQUEST_REVIEW_STATE_CHANGED:
                if (PullRequestReviewAction.DONE.name().equals(newValue)) {
                    return Messages.get(lang, "notification.pullrequest.reviewed", User.find.byId(senderId).loginId);
                } else {
                    return Messages.get(lang, "notification.pullrequest.unreviewed", User.find.byId(senderId).loginId);
                }
            case REVIEW_THREAD_STATE_CHANGED:
                if (newValue.equals(CommentThread.ThreadState.CLOSED.name())) {
                    return Messages.get(lang, "notification.reviewthread.closed");
                } else {
                    return Messages.get(lang, "notification.reviewthread.reopened");
                }
            case ISSUE_MOVED:
                    return Messages.get(lang, "notification.type.issue.moved", oldValue, newValue);
            case ISSUE_SHARER_CHANGED:
                if (StringUtils.isNotBlank(newValue)) {
                    User user = User.findByLoginId(newValue);
                    return Messages.get(lang, "notification.issue.sharer.added", user.getDisplayName(user));
                } else if (StringUtils.isNotBlank(oldValue)) {
                    return Messages.get(lang, "notification.issue.sharer.deleted");
                }
            case ISSUE_LABEL_CHANGED:
                if (StringUtils.isNotBlank(newValue)) {
                    User user = User.findByLoginId(newValue);
                    return Messages.get(lang, "notification.issue.label.added", user.getDisplayName(user));
                } else if (StringUtils.isNotBlank(oldValue)) {
                    return Messages.get(lang, "notification.issue.label.deleted");
                }
            case RESOURCE_DELETED:
                User user = User.findByLoginId(newValue);
                return Messages.get(lang, "notification.resource.deleted", user.getDisplayName(user));
            default:
                play.Logger.warn("Unknown event message: " + this);
                play.Logger.warn("Event Type: " + eventType);
                play.Logger.warn("See: NotificationEvent.getMessage");
                return eventType.getDescr();
        }
    }

    @Transient
    public String getPlainMessage() {
        return getPlainMessage(Lang.defaultLang());
    }

    @Transient
    public String getPlainMessage(Lang lang) {
        switch(eventType) {
            case ISSUE_BODY_CHANGED:
            case POSTING_BODY_CHANGED:
                return DiffUtil.getDiffPlainText(oldValue, newValue);
            default:
                return getMessage(lang).replaceAll("\n\n<br />\n", "\n\n");
        }
    }

    /**
     * Builds a notification message for a comment on code.
     *
     * The message contains the commented hunk of the code as below:
     *
     *     In foo.c:
     *
     *     > @@ -1,5 +1,5 @@
     *     >   int bar(void)
     *     >   {
     *     > -     printf("bad");
     *     > +     printf("good");
     *
     *     Looks good to me
     *
     *     >       return 0;
     *     >   }
     *
     * Note: This method has a performance issue. See the comment in the method
     * body for the details.
     *
     * @param reviewComment
     * @param lang
     * @return
     * @throws IOException
     */
    private static String buildCommentedCodeMessage(ReviewComment reviewComment, Lang lang) throws
            IOException {
        if (reviewComment.thread == null ||
            !reviewComment.thread.getFirstReviewComment().equals(reviewComment) ||
            !(reviewComment.thread instanceof CodeCommentThread)) {
            return reviewComment.getContents();
        }

        CodeCommentThread thread = (CodeCommentThread) reviewComment.thread;

        PlayRepository repo;

        try {
            repo = RepositoryService.getRepository(thread.project);
        } catch (Exception e) {
            play.Logger.error("Failed to get the repository", e);
            return reviewComment.getContents();
        }

        CodeRange codeRange = thread.codeRange;


        List<FileDiff> diffs;
        if (thread.prevCommitId == null) {
            diffs = repo.getDiff(thread.commitId);
        } else {
            diffs = repo.getDiff(thread.prevCommitId, thread.commitId);
        }

        for(FileDiff diff : diffs) {
            if (!codeRange.isFor(diff)) continue;

            StringBuilder message = new StringBuilder();

            message.append(Messages.get(lang,
                    "notification.reviewthread.inTheFile", codeRange.path));
            message.append("\n");

            diff.setInterestLine(codeRange.endLine);
            diff.setInterestSide(codeRange.endSide);

            // FIXME: Performance Issue: The hunks of this diffs were
            // already computed but it was not necessary because they will
            // and should be recomputed here.
            FileDiff.Hunks hunks = diff.getHunks();
            if (hunks != null) {
                message.append("```diff\n");
                for (Hunk hunk : hunks) {
                    message.append(
                            String.format("> @@ -%d, %d +%d, %d @@\n",
                                    hunk.beginA + 1, (hunk.endA - hunk.beginA),
                                    hunk.beginB + 1, (hunk.endB - hunk.beginB)));
                    for (DiffLine line : hunk.lines) {
                        message.append("> ");
                        switch (line.kind) {
                            case CONTEXT:
                                message.append(" ");
                                break;
                            case ADD:
                                message.append("+");
                                break;
                            case REMOVE:
                                message.append("-");
                                break;
                        }
                        message.append(line.content + "\n");
                        if (codeRange.endsWith(line)) {
                            message.append("```\n");
                            message.append("\n" + reviewComment.getContents() + "\n\n");
                            message.append("```diff\n");
                        }
                    }
                }
                message.append("```\n");
            } else {
                message.append(reviewComment.getContents());
            }

            return message.toString();
        }

        return reviewComment.getContents();
    }

    public User getSender() {
        return User.find.byId(this.senderId);
    }

    public Resource getResource() {
        return Resource.get(resourceType, resourceId);
    }

    public Project getProject() {
        switch(resourceType) {
            case ISSUE_ASSIGNEE:
                return Assignee.finder.byId(Long.valueOf(resourceId)).project;
            case PROJECT:
                return Project.find.byId(Long.valueOf(resourceId));
            default:
                Resource resource = getResource();
                if (resource != null) {
                    if (resource instanceof GlobalResource) {
                        return null;
                    } else {
                        return resource.getProject();
                    }
                } else {
                    return null;
                }
        }
    }

    public Organization getOrganization() {
        switch (resourceType) {
            case ORGANIZATION:
                return Organization.find.byId(Long.valueOf(resourceId));
            default:
                return null;
        }
    }

    public boolean resourceExists() {
        return Resource.exists(resourceType, resourceId);
    }

    public static void add(NotificationEvent event) {
        if (event.notificationMail == null) {
            event.notificationMail = new NotificationMail();
            event.notificationMail.notificationEvent = event;
        }

        Date draftDate = DateTime.now().minusMillis(EventConstants.DRAFT_TIME_IN_MILLIS).toDate();

        NotificationEvent lastEvent = NotificationEvent.find.where()
                .eq("resourceId", event.resourceId)
                .eq("resourceType", event.resourceType)
                .gt("created", draftDate)
                .orderBy("id desc").setMaxRows(1).findUnique();

        if (lastEvent != null) {
            if (isSameUserEventAsPrevious(event, lastEvent)) {
                // If the last event is A -> B and the current event is B -> C,
                // they are merged into the new event A -> C.
                event.oldValue = lastEvent.getOldValue();
                lastEvent.delete();

                // If the last event is A -> B and the current event is B -> A,
                // they are removed.
                if (StringUtils.equals(event.oldValue, event.newValue)) {
                    return;
                }
            }
        }

        filterReceivers(event);
        if (event.receivers.isEmpty()) {
            return;
        }
        event.save();
        event.saveManyToManyAssociations("receivers");
    }

    public static void addWithoutSkipEvent(NotificationEvent event) {
        if (event.notificationMail == null) {
            event.notificationMail = new NotificationMail();
            event.notificationMail.notificationEvent = event;
        }

        Date draftDate = DateTime.now().minusMillis(EventConstants.DRAFT_TIME_IN_MILLIS).toDate();

        NotificationEvent lastEvent = NotificationEvent.find.where()
                .eq("resourceId", event.resourceId)
                .eq("resourceType", event.resourceType)
                .gt("created", draftDate)
                .orderBy("id desc").setMaxRows(1).findUnique();

        if (lastEvent != null) {
            if (isSameUserEventAsPrevious(event, lastEvent) &&
                    isRevertingTheValue(event, lastEvent)) {
                lastEvent.delete();
                return;
            }
        }

        if(isAddingSharerEvent(event)){
            filterReceivers(event);
        }

        if (event.receivers.isEmpty()) {
            return;
        }
        event.save();
        event.saveManyToManyAssociations("receivers");
    }

    private static boolean isSameUserEventAsPrevious(NotificationEvent event, NotificationEvent lastEvent) {
        return lastEvent.eventType == event.eventType &&
                event.senderId.equals(lastEvent.senderId);
    }

    private static boolean isRevertingTheValue(NotificationEvent event, NotificationEvent lastEvent) {
        return StringUtils.equals(event.oldValue, lastEvent.newValue) &&
                StringUtils.equals(event.newValue, lastEvent.oldValue);
    }

    private static boolean isAddingSharerEvent(NotificationEvent event) {
        return event.eventType.equals(EventType.ISSUE_SHARER_CHANGED)
            && StringUtils.isBlank(event.oldValue)
            && StringUtils.isNotBlank(event.newValue);
    }

    private static void filterReceivers(final NotificationEvent event) {
        final Project project = event.getProject();
        if (project == null) {
            return;
        }

        final Resource resource = project.asResource();
        CollectionUtils.filter(event.receivers, new Predicate() {
            @Override
            public boolean evaluate(Object obj) {
                User receiver = (User) obj;
                if(receiver.loginId == null) {
                    return false;
                }

                if (!AccessControl.isAllowed(receiver, event.getResource(), Operation.READ)) {
                    return false;
                }

                if (!Watch.isWatching(receiver, resource)) {
                    return true;
                }
                return UserProjectNotification.isEnabledNotiType(receiver, project, event.eventType);
            }
        });
    }

    public static void deleteBy(Resource resource) {
        for (NotificationEvent event : NotificationEvent.find.where().where().eq("resourceType",
                resource.getType()).eq("resourceId", resource.getId()).findList()) {
            event.delete();
        }
    }

    /**
     * @see {@link controllers.PullRequestApp#newPullRequest(String, String)}
     */
    public static NotificationEvent afterNewPullRequest(User sender, PullRequest pullRequest) {
        NotificationEvent notiEvent = createFrom(sender, pullRequest);
        notiEvent.title = formatNewTitle(pullRequest);
        notiEvent.receivers = getReceiversWithRelatedAuthors(sender, pullRequest);
        notiEvent.eventType = NEW_PULL_REQUEST;
        notiEvent.oldValue = null;
        notiEvent.newValue = pullRequest.body;
        NotificationEvent.add(notiEvent);

        return notiEvent;
    }

    public String getUrlToView() {
        Organization organization;
        switch(eventType) {
            case MEMBER_ENROLL_REQUEST:
                if (getProject() == null) {
                    return null;
                } else {
                    return routes.ProjectApp.members(
                            getProject().owner, getProject().name).url();
                }
            case MEMBER_ENROLL_ACCEPT:
                if (getProject() == null) {
                    return null;
                } else {
                    return routes.ProjectApp.project(
                            getProject().owner, getProject().name).url();
                }
            case ORGANIZATION_MEMBER_ENROLL_REQUEST:
                organization = getOrganization();
                if (organization == null) {
                    return null;
                }
                return routes.OrganizationApp.members(organization.name).url();
            case ORGANIZATION_MEMBER_ENROLL_ACCEPT:
                organization = getOrganization();
                if (organization == null) {
                    return null;
                }
                return routes.OrganizationApp.organization(organization.name).url();
            case NEW_COMMIT:
                if (getProject() == null) {
                    return null;
                } else {
                    return routes.CodeHistoryApp.historyUntilHead(
                            getProject().owner, getProject().name).url();
                }
            default:
                return RouteUtil.getUrl(resourceType, resourceId);
        }
    }

    @Override
    public Date getCreatedDate() {
        return created;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public EventType getType() {
        return eventType;
    }

    @Override
    public ResourceType getResourceType() {
        return resourceType;
    }

    @Override
    public String getResourceId() {
        return resourceId;
    }

    private static void webhookRequest(EventType eventTypes, PullRequest pullRequest, Boolean gitPushOnly) {
        List<Webhook> webhookList = eventTypes == PULL_REQUEST_MERGED ? Webhook.findByProject(pullRequest.toProject.id) : Webhook.findByProject(pullRequest.toProjectId);
        for (Webhook webhook : webhookList) {
            if (gitPushOnly == webhook.gitPushOnly) {
                // Send push event via webhook payload URLs.
                webhook.sendRequestToPayloadUrl(eventTypes, UserApp.currentUser(), pullRequest);
            }
        }
    }

    private static void webhookRequest(EventType eventTypes, PullRequest pullRequest, PullRequestReviewAction reviewAction, Boolean gitPushOnly) {
        List<Webhook> webhookList = Webhook.findByProject(pullRequest.toProject.id);
        for (Webhook webhook : webhookList) {
            if (gitPushOnly == webhook.gitPushOnly) {
                // Send push event via webhook payload URLs.
                webhook.sendRequestToPayloadUrl(eventTypes, UserApp.currentUser(), pullRequest, reviewAction);
            }
        }
    }

    private static void webhookRequest(EventType eventTypes, Issue issue, Boolean gitPushOnly) {
        List<Webhook> webhookList = Webhook.findByProject(issue.project.id);
        for (Webhook webhook : webhookList) {
            if (gitPushOnly == webhook.gitPushOnly) {
                // Send push event via webhook payload URLs.
                webhook.sendRequestToPayloadUrl(eventTypes, UserApp.currentUser(), issue);
            }
        }
    }

    private static void webhookRequest(EventType eventTypes, Issue issue, Project previous, Boolean gitPushOnly) {
        List<Webhook> webhookList = Webhook.findByProject(issue.project.id);
        for (Webhook webhook : webhookList) {
            if (gitPushOnly == webhook.gitPushOnly) {
                // Send push event via webhook payload URLs.
                webhook.sendRequestToPayloadUrl(eventTypes, UserApp.currentUser(), issue, previous);
            }
        }
    }

    private static void webhookRequest(EventType eventTypes, Comment comment, Boolean gitPushOnly) {
        List<Webhook> webhookList = Webhook.findByProject(comment.projectId);
        for (Webhook webhook : webhookList) {
            if (gitPushOnly == webhook.gitPushOnly) {
                // Send push event via webhook payload URLs.
                webhook.sendRequestToPayloadUrl(eventTypes, UserApp.currentUser(), comment);
            }
        }
    }

    private static void webhookRequest(EventType eventTypes, PullRequest pullRequest, ReviewComment reviewComment, Boolean gitPushOnly) {
        List<Webhook> webhookList = Webhook.findByProject(pullRequest.toProject.id);
        for (Webhook webhook : webhookList) {
            if (gitPushOnly == webhook.gitPushOnly) {
                // Send push event via webhook payload URLs.
                webhook.sendRequestToPayloadUrl(eventTypes, UserApp.currentUser(), pullRequest, reviewComment);
            }
        }
    }

    private static void webhookRequest(Project project, List<RevCommit> commits, List<String> refNames, User sender, String title, Boolean gitPushOnly) {
        List<Webhook> webhookList = Webhook.findByProject(project.id);
        for (Webhook webhook : webhookList) {
            if (gitPushOnly == webhook.gitPushOnly) {
                // Send push event via webhook payload URLs.
                webhook.sendRequestToPayloadUrl(commits, refNames, sender, title);
            }
        }
    }

    /**
     * @see {@link models.PullRequest#merge(models.PullRequestEventMessage)}
     * @see {@link controllers.PullRequestApp#addNotification(models.PullRequest, models.enumeration.State, models.enumeration.State)}
     */
    public static NotificationEvent afterPullRequestUpdated(User sender, PullRequest pullRequest, State oldState, State newState) {
        NotificationEvent notiEvent = createFrom(sender, pullRequest);
        notiEvent.title = formatReplyTitle(pullRequest);
        notiEvent.receivers = getReceivers(sender, pullRequest);
        notiEvent.eventType = PULL_REQUEST_STATE_CHANGED;
        notiEvent.oldValue = oldState.state();
        notiEvent.newValue = newState.state();
        NotificationEvent.add(notiEvent);

        if (newState == State.MERGED) {
            webhookRequest(PULL_REQUEST_MERGED, pullRequest, false);
        }

        return notiEvent;
    }

    public static NotificationEvent afterPullRequestCommitChanged(User sender, PullRequest pullRequest) {
        NotificationEvent notiEvent = createFrom(sender, pullRequest);
        notiEvent.title = formatReplyTitle(pullRequest);
        notiEvent.receivers = getReceivers(sender, pullRequest);
        notiEvent.eventType = PULL_REQUEST_COMMIT_CHANGED;
        notiEvent.oldValue = null;
        notiEvent.newValue = newPullRequestCommitChangedMessage(pullRequest);
        NotificationEvent.add(notiEvent);

        webhookRequest(PULL_REQUEST_COMMIT_CHANGED, pullRequest, false);

        return notiEvent;
    }

    private static String newPullRequestCommitChangedMessage(PullRequest pullRequest) {
        List<PullRequestCommit> commits = PullRequestCommit.find.where().eq("pullRequest", pullRequest).orderBy().desc("authorDate").findList();
        StringBuilder builder = new StringBuilder();
        builder.append("### ");
        builder.append(Messages.get("notification.pullrequest.current.commits"));
        builder.append("\n");
        for (PullRequestCommit commit : commits) {
            if (commit.state == PullRequestCommit.State.CURRENT) {
                builder.append(commit.getCommitShortId());
                builder.append(" ");
                builder.append(commit.getCommitShortMessage());
                builder.append("\n");
            }
        }
        return builder.toString();
    }

    /**
     * @see {@link actors.PullRequestActor#processPullRequestMerging(models.PullRequestEventMessage, models.PullRequest)}
     */
    public static NotificationEvent afterMerge(User sender, PullRequest pullRequest, State state) {
        NotificationEvent notiEvent = createFrom(sender, pullRequest);
        notiEvent.title = formatReplyTitle(pullRequest);
        notiEvent.receivers = state == State.MERGED ? getReceiversWithRelatedAuthors(sender, pullRequest) : getReceivers(sender, pullRequest);
        notiEvent.eventType = PULL_REQUEST_MERGED;
        notiEvent.newValue = state.state();
        NotificationEvent.add(notiEvent);
        return notiEvent;
    }

    /**
     * @see {@link controllers.PullRequestApp#newComment(String, String, Long, String)}
     */
    public static void afterNewComment(User sender, PullRequest pullRequest,
                                       ReviewComment newComment, String urlToView) {
        NotificationEvent.add(forNewComment(sender, pullRequest, newComment));
    }

    public static NotificationEvent forNewComment(User sender, PullRequest pullRequest, ReviewComment newComment) {
        webhookRequest(NEW_REVIEW_COMMENT, pullRequest, newComment, false);

        NotificationEvent notiEvent = createFrom(sender, newComment);
        notiEvent.title = formatReplyTitle(pullRequest);
        Set<User> receivers = getMentionedUsers(newComment.getContents());
        receivers.addAll(getReceivers(sender, pullRequest));
        receivers.remove(User.findByLoginId(newComment.author.loginId));
        notiEvent.receivers = receivers;
        notiEvent.eventType = NEW_REVIEW_COMMENT;
        notiEvent.oldValue = null;
        notiEvent.newValue = newComment.getContents();
        return notiEvent;
    }

    public static NotificationEvent afterNewPullRequest(PullRequest pullRequest) {
        webhookRequest(NEW_PULL_REQUEST, pullRequest, false);
        return afterNewPullRequest(UserApp.currentUser(), pullRequest);
    }

    public static NotificationEvent afterPullRequestUpdated(PullRequest pullRequest, State oldState, State newState) {
        return afterPullRequestUpdated(UserApp.currentUser(), pullRequest, oldState, newState);
    }

    public static void afterNewComment(Comment comment) {
        webhookRequest(NEW_COMMENT, comment, false);
        NotificationEvent.add(forNewComment(comment, UserApp.currentUser()));
    }

    public static NotificationEvent forComment(Comment comment, User author, EventType eventType) {
        AbstractPosting post = comment.getParent();

        NotificationEvent notiEvent = createFrom(author, comment);
        notiEvent.title = formatReplyTitle(post);
        notiEvent.eventType = eventType;
        notiEvent.receivers = getMandatoryReceivers(comment, eventType);
        notiEvent.oldValue = comment.previousContents;
        notiEvent.newValue = comment.contents;
        notiEvent.resourceType = comment.asResource().getType();
        notiEvent.resourceId = comment.asResource().getId();
        return notiEvent;
    }

    public static NotificationEvent forUpdatedComment(Comment comment, User author) {
        return forComment(comment, author, COMMENT_UPDATED);
    }

    public static NotificationEvent forNewComment(Comment comment, User author) {
        return forComment(comment, author, NEW_COMMENT);
    }

    public static void afterNewCommentWithState(Comment comment, State state) {
        AbstractPosting post = comment.getParent();

        NotificationEvent notiEvent = createFromCurrentUser(comment);
        notiEvent.title = formatReplyTitle(post);
        Set<User> receivers = getReceivers(post);
        receivers.addAll(getMentionedUsers(comment.contents));
        receivers.remove(UserApp.currentUser());
        notiEvent.receivers = receivers;
        notiEvent.eventType = NEW_COMMENT;
        notiEvent.oldValue = null;
        notiEvent.newValue = comment.contents + "\n" + state.state();
        notiEvent.resourceType = comment.asResource().getType();
        notiEvent.resourceId = comment.asResource().getId();

        NotificationEvent.add(notiEvent);
    }

    public static NotificationEvent afterStateChanged(State oldState, Issue issue) {
        webhookRequest(ISSUE_STATE_CHANGED, issue, false);

        NotificationEvent notiEvent = createFromCurrentUser(issue);
        notiEvent.title = formatReplyTitle(issue);
        notiEvent.receivers = getMandatoryReceivers(issue, EventType.ISSUE_STATE_CHANGED);
        notiEvent.eventType = ISSUE_STATE_CHANGED;
        notiEvent.oldValue = oldState != null ? oldState.state() : null;
        notiEvent.newValue = issue.state.state();
        NotificationEvent.add(notiEvent);
        return notiEvent;
    }

    public static NotificationEvent afterStateChanged(
            CommentThread.ThreadState oldState, CommentThread thread)
            throws IOException, SVNException, ServletException {
        NotificationEvent notiEvent = createFromCurrentUser(thread);

        notiEvent.eventType = REVIEW_THREAD_STATE_CHANGED;
        notiEvent.oldValue = oldState.name() != null ? oldState.name() : null;
        notiEvent.newValue = thread.state.name();

        // Set receivers
        Set<User> receivers;
        if (thread.isOnPullRequest()) {
            PullRequest pullRequest = thread.pullRequest;
            notiEvent.title = formatReplyTitle(pullRequest);
            receivers = pullRequest.getWatchers();
        } else {
            String commitId;
            if (thread instanceof CodeCommentThread) {
                commitId = ((CodeCommentThread)thread).commitId;
            } else {
                commitId = ((NonRangedCodeCommentThread)thread).commitId;
            }
            Project project = thread.project;
            Commit commit = RepositoryService.getRepository(project).getCommit(commitId);
            notiEvent.title = formatReplyTitle(project, commit);
            receivers = commit.getWatchers(project);
        }
        receivers.remove(UserApp.currentUser());
        notiEvent.receivers = receivers;

        NotificationEvent.add(notiEvent);

        return notiEvent;
    }

    public static NotificationEvent afterAssigneeChanged(User oldAssignee, Issue issue) {
        webhookRequest(ISSUE_ASSIGNEE_CHANGED, issue, false);

        NotificationEvent notiEvent = createFromCurrentUser(issue);

        Set<User> receivers = getReceiversWhenAssigneeChanged(oldAssignee, issue);
        if(oldAssignee != null) {
            notiEvent.oldValue = oldAssignee.loginId;
        }

        if (issue.assignee != null) {
            notiEvent.newValue = User.find.byId(issue.assignee.user.id).loginId;
        }
        notiEvent.title = formatReplyTitle(issue);
        notiEvent.receivers = receivers;
        notiEvent.eventType = ISSUE_ASSIGNEE_CHANGED;

        NotificationEvent.add(notiEvent);

        return notiEvent;
    }

    private static Set<User> getReceiversWhenAssigneeChanged(User oldAssignee, Issue issue) {
        Set<User> receivers = getMandatoryReceivers(issue, ISSUE_ASSIGNEE_CHANGED);

        if (oldAssignee != null && !oldAssignee.isAnonymous()
                && !oldAssignee.loginId.equals(UserApp.currentUser().loginId)) {
            receivers.add(oldAssignee);
        }

        return receivers;
    }

    public static NotificationEvent afterNewIssue(Issue issue) {
        NotificationEvent notiEvent = forNewIssue(issue, UserApp.currentUser());
        NotificationEvent.add(notiEvent);
        webhookRequest(NEW_ISSUE, issue, false);
        return notiEvent;
    }

    public static NotificationEvent forNewIssue(Issue issue, User author) {
        NotificationEvent notiEvent = createFrom(author, issue);
        notiEvent.title = formatNewTitle(issue);
        notiEvent.receivers = getReceivers(issue, author);
        notiEvent.eventType = NEW_ISSUE;
        notiEvent.oldValue = null;
        notiEvent.newValue = issue.body;
        return notiEvent;
    }

    public static NotificationEvent afterResourceDeleted(AbstractPosting item, User reuqestedUser) {
        NotificationEvent notiEvent = createFrom(reuqestedUser, item.project);
        notiEvent.title = formatNewTitle(item);
        notiEvent.receivers = getReceivers(item, reuqestedUser);
        notiEvent.eventType = RESOURCE_DELETED;
        notiEvent.oldValue = item.body;
        notiEvent.newValue = reuqestedUser.loginId;

        NotificationEvent.add(notiEvent);
        if (item instanceof Issue) {
            webhookRequest(RESOURCE_DELETED, (Issue)item, false);
        }
        return notiEvent;
    }

    public static NotificationEvent afterIssueBodyChanged(String oldBody, Issue issue) {
        webhookRequest(ISSUE_BODY_CHANGED, issue, false);

        NotificationEvent notiEvent = createFromCurrentUser(issue);
        notiEvent.title = formatReplyTitle(issue);
        notiEvent.receivers = getReceiversForIssueBodyChanged(oldBody, issue);
        notiEvent.eventType = EventType.ISSUE_BODY_CHANGED;
        notiEvent.oldValue = oldBody;
        notiEvent.newValue = issue.body;

        NotificationEvent.add(notiEvent);

        return notiEvent;
    }

    public static NotificationEvent afterIssueMoved(Project previous, Issue issue, Supplier<Set<User>> getReceivers) {
        webhookRequest(ISSUE_MOVED, issue, previous, false);

        NotificationEvent notiEvent = createFromCurrentUser(issue);
        notiEvent.title = formatReplyTitle(issue);
        notiEvent.receivers = getReceivers.get();
        notiEvent.eventType = ISSUE_MOVED;
        notiEvent.oldValue = previous != null ? previous.owner + "/" + previous.name : null;
        notiEvent.newValue = issue.project.owner + "/" + issue.project.name;

        NotificationEvent.add(notiEvent);

        return notiEvent;
    }

    public static NotificationEvent afterIssueSharerChanged(Issue issue, String sharerLoginId, String action) {
        NotificationEvent notiEvent = createFromCurrentUser(issue);
        notiEvent.title = formatReplyTitle(issue);
        notiEvent.receivers = findSharer(sharerLoginId);
        notiEvent.eventType = ISSUE_SHARER_CHANGED;
        if (IssueSharer.ADD.equalsIgnoreCase(action)) {
            notiEvent.oldValue = "";
            notiEvent.newValue = sharerLoginId;
        } else if (IssueSharer.DELETE.equalsIgnoreCase(action)) {
            notiEvent.oldValue = sharerLoginId;
            notiEvent.newValue = "";
        }

        NotificationEvent.addWithoutSkipEvent(notiEvent);

        return notiEvent;
    }

    private static Set<User> findSharer(String sharerLoginId) {
        Set<User> receivers = new HashSet<>();
        receivers.add(User.findByLoginId(sharerLoginId));
        return receivers;
    }

    public static NotificationEvent afterIssueLabelChanged(String addedLabels, String deletedLabels, Issue issue) {
        NotificationEvent notiEvent = createFromCurrentUser(issue);
        notiEvent.title = formatReplyTitle(issue);
        notiEvent.receivers = null; // no receivers
        notiEvent.eventType = ISSUE_LABEL_CHANGED;
        notiEvent.oldValue = deletedLabels;
        notiEvent.newValue = addedLabels;

        NotificationEvent.addWithoutSkipEvent(notiEvent);
        return notiEvent;
    }

    public static NotificationEvent afterMilestoneChanged(Long oldMilestoneId, Issue issue) {
        if (issue.milestone != null) {
            issue.milestone.refresh();
        }
        webhookRequest(ISSUE_MILESTONE_CHANGED, issue, false);

        NotificationEvent notiEvent = createFromCurrentUser(issue);

        Set<User> receivers = getMandatoryReceivers(issue, ISSUE_MILESTONE_CHANGED);

        notiEvent.title = formatReplyTitle(issue);
        notiEvent.receivers = receivers;
        notiEvent.eventType = ISSUE_MILESTONE_CHANGED;
        notiEvent.oldValue = oldMilestoneId.toString();
        notiEvent.newValue = issue.milestoneId().toString();

        NotificationEvent.add(notiEvent);

        return notiEvent;
    }

    private static Set<User> getMandatoryReceivers(Issue issue, EventType eventType) {
        Set<User> receivers = findWatchers(issue.asResource());
        receivers.add(issue.getAuthor());

        for (IssueSharer issueSharer : issue.sharers) {
            receivers.add(User.findByLoginId(issueSharer.loginId));
        }

        if (issue.assignee != null) {
            receivers.add(issue.assignee.user);
        }

        receivers.addAll(findWatchers(issue.asResource()));
        receivers.addAll(findEventWatchersByEventType(issue.project.id, eventType));

        receivers.removeAll(findUnwatchers(issue.asResource()));
        receivers.removeAll(findEventUnwatchersByEventType(issue.project.id, eventType));
        receivers.remove(findCurrentUserToBeExcluded(issue.authorId));

        return receivers;
    }

    private static User findCurrentUserToBeExcluded(Long authorId) {
        User currentUser;
        try {
            currentUser = UserApp.currentUser();
        } catch (RuntimeException re) {
            // expectation: "There is no HTTP Context available from here" runtime exception
            currentUser = User.anonymous;
        }

        if (currentUser.isAnonymous()) {
            // It is assumed that it is called by author and processed by system.
            return User.find.byId(authorId);
        } else {
            return currentUser;
        }
    }

    private static Set<User> getMandatoryReceivers(Posting posting, EventType eventType) {
        Set<User> receivers = findWatchers(posting.asResource());
        receivers.add(posting.getAuthor());
        receivers.addAll(findWatchers(posting.asResource()));
        receivers.addAll(findEventWatchersByEventType(posting.project.id, eventType));

        receivers.removeAll(findUnwatchers(posting.asResource()));
        receivers.removeAll(findEventUnwatchersByEventType(posting.project.id, eventType));
        receivers.remove(findCurrentUserToBeExcluded(posting.authorId));

        return receivers;
    }

    private static Set<User> getMandatoryReceivers(Comment comment, EventType eventType) {
        AbstractPosting parent = comment.getParent();
        Set<User> receivers = findWatchers(parent.asResource());
        receivers.add(parent.getAuthor());
        receivers.addAll(getMentionedUsers(comment.contents));
        includeAssigneeIfExist(comment, receivers);
        Comment parentComment = comment.getParentComment();
        if (parentComment != null) {
            receivers.remove(User.find.byId(comment.getParent().authorId));
            receivers.add(User.find.byId(comment.getParentComment().authorId));

            if (parentComment.authorId.equals(comment.authorId)) { // when parent comment author is writing sub-comment
                for(Comment subComment: comment.getSiblingComments()) {
                    receivers.add(User.find.byId(subComment.authorId));
                }
            }
        }

        receivers.addAll(findEventWatchersByEventType(comment.projectId, eventType));

        receivers.removeAll(findUnwatchers(parent.asResource()));
        receivers.removeAll(findEventUnwatchersByEventType(comment.projectId, eventType));
        receivers.remove(findCurrentUserToBeExcluded(comment.authorId));

        return receivers;
    }

    private static Set<User> getProjectCommitReceivers(Project project, EventType eventType, User sender) {
        Set<User> receivers = findMembersOnlyFromWatchers(project);
        receivers.removeAll(findUnwatchers(project.asResource()));
        receivers.removeAll(findEventUnwatchersByEventType(project.id, eventType));
        receivers.remove(sender);

        return receivers;
    }

    private static Set<User> findMembersOnlyFromWatchers(Project project) {
        Set<User> receivers = new HashSet<>();
        Set<User> projectMembers = extractMembers(project);
        for (User watcher : findWatchers(project.asResource())) {
            if (projectMembers.contains(watcher)) {
                receivers.add(watcher);
            }
        }
        return receivers;
    }

    private static Set<User> extractMembers(Project project) {
        Set<User> projectMembers = new HashSet<>();
        for (ProjectUser projectUser : project.members()) {
            projectMembers.add(projectUser.user);
        }
        return projectMembers;
    }

    private static Set<User> getReceiversForIssueBodyChanged(String oldBody, Issue issue) {
        Set<User> receivers = getMandatoryReceivers(issue, ISSUE_BODY_CHANGED);
        receivers.addAll(getNewMentionedUsers(oldBody, issue.body));
        receivers.remove(findCurrentUserToBeExcluded(issue.authorId));
        return receivers;
    }

    public static void afterNewPost(Posting post) {
        NotificationEvent.add(forNewPosting(post, UserApp.currentUser()));
    }

    public static void afterUpdatePosting(String oldValue, Posting post) {
        NotificationEvent.add(forUpdatePosting(oldValue, post, UserApp.currentUser()));
    }

    public static NotificationEvent forNewPosting(Posting post, User author) {
        NotificationEvent notiEvent = createFrom(author, post);
        notiEvent.title = formatNewTitle(post);
        notiEvent.receivers = getReceivers(post);
        notiEvent.eventType = NEW_POSTING;
        notiEvent.oldValue = null;
        notiEvent.newValue = post.body;
        return notiEvent;
    }

    public static NotificationEvent forUpdatePosting(String oldValue, Posting post, User author) {
        NotificationEvent notiEvent = createFrom(author, post);
        notiEvent.title = formatNewTitle(post);
        notiEvent.receivers = getMandatoryReceivers(post, EventType.POSTING_BODY_CHANGED);
        notiEvent.eventType = POSTING_BODY_CHANGED;
        notiEvent.oldValue = oldValue;
        notiEvent.newValue = post.body;
        return notiEvent;
    }

    public static void afterNewCommitComment(Project project, ReviewComment comment,
                                             String commitId) throws
            IOException, SVNException, ServletException {
        NotificationEvent.add(
                forNewCommitComment(project, comment, commitId, UserApp.currentUser()));
    }

    public static NotificationEvent forNewCommitComment(
            Project project, ReviewComment comment, String commitId, User author)
            throws IOException, SVNException, ServletException {
        Commit commit = RepositoryService.getRepository(project).getCommit(commitId);
        Set<User> watchers = commit.getWatchers(project);
        watchers.addAll(getMentionedUsers(comment.getContents()));
        watchers.remove(author);

        NotificationEvent notiEvent = createFrom(author, comment);
        notiEvent.title = formatReplyTitle(project, commit);
        notiEvent.receivers = watchers;
        notiEvent.eventType = NEW_REVIEW_COMMENT;
        notiEvent.oldValue = null;
        notiEvent.newValue = comment.getContents();
        return notiEvent;
    }

    public static void afterNewSVNCommitComment(Project project, CommitComment codeComment)
            throws IOException, SVNException, ServletException {
        NotificationEvent.add(forNewSVNCommitComment(project, codeComment, UserApp.currentUser()));
    }

    private static NotificationEvent forNewSVNCommitComment(
            Project project, CommitComment codeComment, User author)
            throws IOException, SVNException, ServletException {
        Commit commit = RepositoryService.getRepository(project).getCommit(codeComment.commitId);
        Set<User> watchers = commit.getWatchers(project);
        watchers.addAll(getMentionedUsers(codeComment.contents));
        watchers.remove(author);

        NotificationEvent notiEvent = createFromCurrentUser(codeComment);
        notiEvent.title = formatReplyTitle(project, commit);
        notiEvent.receivers = watchers;
        notiEvent.eventType = NEW_COMMENT;
        notiEvent.oldValue = null;
        notiEvent.newValue = codeComment.contents;
        return notiEvent;
    }

    public static void afterMemberRequest(Project project, User user, RequestState state) {
        NotificationEvent notiEvent = createFromCurrentUser(project);
        notiEvent.eventType = MEMBER_ENROLL_REQUEST;
        notiEvent.receivers = getReceivers(project);
        notiEvent.newValue = state.name();
        if (state == RequestState.ACCEPT || state == RequestState.REJECT) {
            notiEvent.receivers.remove(UserApp.currentUser());
            notiEvent.receivers.add(user);
        }

        switch (state) {
            case REQUEST:
                notiEvent.title = formatMemberRequestTitle(project, user);
                notiEvent.oldValue = RequestState.CANCEL.name();
                break;
            case CANCEL:
                notiEvent.title = formatMemberRequestCancelTitle(project, user);
                notiEvent.oldValue = RequestState.REQUEST.name();
                break;
            case ACCEPT:
                notiEvent.title = formatMemberAcceptTitle(project, user);
                notiEvent.eventType = MEMBER_ENROLL_ACCEPT;
                notiEvent.oldValue = RequestState.REQUEST.name();
                break;
        }

        notiEvent.resourceType = project.asResource().getType();
        notiEvent.resourceId = project.asResource().getId();
        NotificationEvent.add(notiEvent);
    }

    public static void afterOrganizationMemberRequest(Organization organization, User user, RequestState state) {
        NotificationEvent notiEvent = createFromCurrentUser(organization);
        notiEvent.eventType = ORGANIZATION_MEMBER_ENROLL_REQUEST;
        notiEvent.receivers = getReceivers(organization);
        notiEvent.newValue = state.name();
        if (state == RequestState.ACCEPT || state == RequestState.REJECT) {
            notiEvent.receivers.remove(UserApp.currentUser());
            notiEvent.receivers.add(user);
        }

        switch (state) {
            case REQUEST:
                notiEvent.title = formatMemberRequestTitle(organization, user);
                notiEvent.oldValue = RequestState.CANCEL.name();
                break;
            case CANCEL:
                notiEvent.title = formatMemberRequestCancelTitle(organization, user);
                notiEvent.oldValue = RequestState.REQUEST.name();
                break;
            case ACCEPT:
                notiEvent.title = formatMemberAcceptTitle(organization, user);
                notiEvent.eventType = ORGANIZATION_MEMBER_ENROLL_ACCEPT;
                notiEvent.oldValue = RequestState.REQUEST.name();
                break;
        }

        notiEvent.resourceType = organization.asResource().getType();
        notiEvent.resourceId = organization.asResource().getId();
        NotificationEvent.add(notiEvent);
    }

    public static void afterNewCommits(List<RevCommit> commits, List<String> refNames, Project project, User sender, String title) {
        NotificationEvent notiEvent = createFrom(sender, project);
        notiEvent.title = title;
        notiEvent.receivers = getProjectCommitReceivers(project, NEW_COMMIT, sender);
        notiEvent.eventType = NEW_COMMIT;
        notiEvent.oldValue = null;
        notiEvent.newValue = newCommitsMessage(commits, refNames, project);
        notiEvent.resourceType = project.asResource().getType();
        notiEvent.resourceId = project.asResource().getId();
        NotificationEvent.add(notiEvent);

        webhookRequest(project, commits, refNames, sender, title, true);
    }

    public static NotificationEvent afterReviewed(PullRequest pullRequest, PullRequestReviewAction reviewAction) {
        webhookRequest(EventType.PULL_REQUEST_REVIEW_STATE_CHANGED, pullRequest, reviewAction, false);

        String title = formatReplyTitle(pullRequest);
        Resource resource = pullRequest.asResource();
        Set<User> receivers = pullRequest.getWatchers();
        receivers.add(pullRequest.contributor);
        User reviewer = UserApp.currentUser();
        receivers.remove(reviewer);

        NotificationEvent notiEvent = new NotificationEvent();
        notiEvent.created = new Date();
        notiEvent.title = title;
        notiEvent.senderId = reviewer.id;
        notiEvent.receivers = receivers;
        notiEvent.resourceId = resource.getId();
        notiEvent.resourceType = resource.getType();
        notiEvent.eventType = EventType.PULL_REQUEST_REVIEW_STATE_CHANGED;
        notiEvent.oldValue = reviewAction.getOppositAction().name();
        notiEvent.newValue = reviewAction.name();

        add(notiEvent);

        return notiEvent;
    }

    private static String newCommitsMessage(List<RevCommit> commits, List<String> refNames, Project project) {
        StringBuilder result = new StringBuilder();

        if(commits.size() > 0) {
            result.append("### " + Messages.get("notification.pushed.newcommits") + "\n");
            result.append("```\n");
            for(RevCommit commit : commits) {
                GitCommit gitCommit = new GitCommit(commit);
                result.append(gitCommit.getShortId());
                result.append(" ");
                result.append(gitCommit.getShortMessage());
                result.append("\n");
            }
            result.append("```\n\n");
        }

        if(refNames.size() > 0) {
            result.append("### " + Messages.get("notification.pushed.branches") + "\n");

            for(String refName: refNames) {
                try {
                    result.append("[" + refName + "](" + routes.CodeHistoryApp.history(project.owner, project.name, URLEncoder.encode(refName, "UTF-8"), "") + ")");
                } catch(UnsupportedEncodingException e){
                    result.append(refName);
                }
                result.append("\n");
            }
        }

        return result.toString();
    }

    private static NotificationEvent createFrom(User sender, ResourceConvertible rc) {
        NotificationEvent notiEvent = new NotificationEvent();
        notiEvent.senderId = sender.id;
        notiEvent.created = new Date();
        Resource resource = rc.asResource();
        notiEvent.resourceId = resource.getId();
        notiEvent.resourceType = resource.getType();
        return notiEvent;
    }

    /**
     * @see {@link #createFrom(models.User, models.resource.ResourceConvertible)}
     */
    private static NotificationEvent createFromCurrentUser(ResourceConvertible rc) {
        return createFrom(UserApp.currentUser(), rc);
    }

    private static Set<User> getReceivers(AbstractPosting abstractPosting) {
        return getReceivers(abstractPosting, UserApp.currentUser());
    }

    private static Set<User> getReceivers(AbstractPosting abstractPosting, User except) {
        Set<User> receivers = abstractPosting.getWatchers();
        receivers.addAll(getMentionedUsers(abstractPosting.body));
        receivers.remove(except);
        return receivers;
    }

    private static void includeAssigneeIfExist(Comment comment, Set<User> receivers) {
        if (comment instanceof IssueComment) {
            Assignee assignee = ((Issue) comment.getParent()).assignee;
            if (assignee != null) {
                receivers.add(assignee.user);
            }
        }
    }

    private static String getPrefixedNumber(AbstractPosting posting) {
        if (posting instanceof Issue) {
            return "#" + posting.getNumber();
        } else {
            return posting.getNumber().toString();
        }
    }

    private static String formatReplyTitle(AbstractPosting posting) {
        return String.format("Re: [%s] %s (%s)",
                posting.project.name, posting.title, getPrefixedNumber(posting));
    }

    private static String formatNewTitle(AbstractPosting posting) {
        return String.format("[%s] %s (%s)",
                posting.project.name, posting.title, getPrefixedNumber(posting));
    }

    private static String formatReplyTitle(Project project, Commit commit) {
        return String.format("Re: [%s] %s (%s)",
                project.name, commit.getShortMessage(), commit.getShortId());
    }

    private static Set<User> getReceivers(User sender, PullRequest pullRequest) {
        Set<User> watchers = getDefaultReceivers(pullRequest);
        watchers.remove(sender);
        return watchers;
    }

    private static Set<User> getDefaultReceivers(PullRequest pullRequest) {
        Set<User> watchers = pullRequest.getWatchers();
        watchers.addAll(getMentionedUsers(pullRequest.body));
        return watchers;
    }

    private static Set<User> getReceiversWithRelatedAuthors(User sender, PullRequest pullRequest) {
        Set<User> receivers = getDefaultReceivers(pullRequest);
        String failureMessage =
                "Failed to get authors related to the pullrequest " + pullRequest;
        try {
            if (pullRequest.mergedCommitIdFrom != null
                    && pullRequest.mergedCommitIdTo != null) {
                receivers.addAll(GitRepository.getRelatedAuthors(
                        new GitRepository(pullRequest.toProject).getRepository(),
                        pullRequest.mergedCommitIdFrom,
                        pullRequest.mergedCommitIdTo));
            }
        } catch (LimitExceededException e) {
            for (ProjectUser member : pullRequest.toProject.members()) {
                receivers.add(member.user);
            }
            play.Logger.info(failureMessage
                    + ": Get all project members instead", e);
        } catch (GitAPIException e) {
            play.Logger.warn(failureMessage, e);
        } catch (IOException e) {
            play.Logger.warn(failureMessage, e);
        }
        receivers.remove(sender);
        return receivers;
    }

    private static String formatNewTitle(PullRequest pullRequest) {
        return String.format("[%s] %s (#%d)",
                pullRequest.toProject.name, pullRequest.title, pullRequest.number);
    }

    private static String formatReplyTitle(PullRequest pullRequest) {
        return String.format("Re: [%s] %s (#%s)",
                pullRequest.toProject.name, pullRequest.title, pullRequest.number);
    }

    private static Set<User> getReceivers(Project project) {
        Set<User> receivers = new HashSet<>();
        List<User> managers = User.findUsersByProject(project.id, RoleType.MANAGER);
        for (User manager : managers) {
            if (Watch.isWatching(manager, project.asResource())) {
                receivers.add(manager);
            }
        }
        return receivers;
    }

    private static Set<User> getReceivers(Organization organization) {
        Set<User> receivers = new HashSet<>();
        List<User> managers = User.findUsersByOrganization(organization.id, RoleType.ORG_ADMIN);
        receivers.addAll(managers);

        return receivers;
    }

    private static String formatMemberRequestTitle(Project project, User user) {
        return Messages.get("notification.member.request.title", project.name, user.loginId);
    }

    private static String formatMemberRequestCancelTitle(Project project, User user) {
        return Messages.get("notification.member.request.cancel.title", project.name, user.loginId);
    }

    private static String formatMemberRequestCancelTitle(Organization organization, User user) {
        return Messages.get("notification.member.request.cancel.title", organization.name, user.loginId);
    }

    private static String formatMemberRequestTitle(Organization organization, User user) {
        return Messages.get("notification.organization.member.request.title", organization.name, user.loginId);
    }

    private static String formatMemberAcceptTitle(Project project, User user) {
        return Messages.get("notification.member.request.accept.title", project.name, user.loginId);
    }

    private static String formatMemberAcceptTitle(Organization organization, User user) {
        return Messages.get("notification.member.request.accept.title", organization.name, user.loginId);
    }

    /**
     * Get mentioned users in {@code body}.
     *
     * @param body
     * @return
     */
    public static Set<User> getMentionedUsers(String body) {
        Matcher matcher = Pattern.compile("@" + User.LOGIN_ID_PATTERN_ALLOW_FORWARD_SLASH).matcher(body);
        Set<User> users = new HashSet<>();
        while(matcher.find()) {
            String mentionWord = matcher.group().substring(1);
            users.addAll(findOrganizationMembers(mentionWord));
            users.addAll(findProjectMembers(mentionWord));
            users.add(User.findByLoginId(mentionWord));
        }
        users.remove(User.anonymous);
        return users;
    }

    /**
     * Get new mentioned users.
     *
     * It gets mentioned users from {@code oldBody} and {@code newBody},
     * subtracts old from new and returns it.
     *
     * @param oldBody
     * @param newBody
     * @return
     */
    public static Set<User> getNewMentionedUsers(String oldBody, String newBody) {
        Set<User> oldBodyMentionedUsers = getMentionedUsers(oldBody);
        Set<User> newBodyMentionedUsers = getMentionedUsers(newBody);

        newBodyMentionedUsers.removeAll(oldBodyMentionedUsers);

        return newBodyMentionedUsers;
    }

    private static Set<User> findOrganizationMembers(String mentionWord) {
        Set<User> users = new HashSet<>();
        Organization org = Organization.findByName(mentionWord);
        if (org != null) {
            for (OrganizationUser orgUser : org.users) {
                users.add(orgUser.user);
            }
        }
        return users;
    }

    private static Set<User> findProjectMembers(String mentionWord) {
        Set<User> users = new HashSet<>();
        if(mentionWord.contains("/")){
            String projectName = mentionWord.substring(mentionWord.lastIndexOf("/")+1);
            String loginId = mentionWord.substring(0, mentionWord.lastIndexOf("/"));
            Project mentionedProject = Project.findByOwnerAndProjectName(loginId, projectName);
            if(mentionedProject == null) {
                return users;
            }
            for(ProjectUser projectUser: mentionedProject.members() ){
                users.add(projectUser.user);
            }
        }
        return users;
    }

    public static void scheduleDeleteOldNotifications() {
        if (EventConstants.KEEP_TIME_IN_DAYS > 0) {
            Akka.system().scheduler().schedule(
                Duration.create(1, TimeUnit.MINUTES),
                Duration.create(1, TimeUnit.DAYS),
                new Runnable() {
                    @Override
                    public void run() {
                        Date threshold = DateTime.now()
                                .minusDays(EventConstants.KEEP_TIME_IN_DAYS).toDate();
                        List<NotificationEvent> olds = find.where().lt("created", threshold).findList();

                        for (NotificationEvent old : olds) {
                            old.delete();
                        }
                    }
                },
                Akka.system().dispatcher()
           );
        }
    }

    public static void onStart() {
        scheduleDeleteOldNotifications();
    }

    /**
     * Finds NotificationEvents that are supposed to be shown to the {@code user}.
     * Paginates it with {@code from} and {@code size}.
     *
     * @param user
     * @param from
     * @param size
     * @return
     */
    public static List<NotificationEvent> findByReceiver(User user, int from, int size) {
        String sql = "select t1.id, t1.title, t1.sender_id, t1.created, t1.resource_type, t1.resource_id, t1.event_type, " +
                "t1.old_value, t1.new_value " +
                "from n4user t0 " +
                "left outer join notification_event_n4user t1z_ on t1z_.n4user_id = t0.id " +
                "left outer join notification_event t1 on t1.id = t1z_.notification_event_id " +
                "left outer join notification_mail t2 on t2.notification_event_id = t1.id " +
                "where t0.id = " + user.id + " and t1.id IS NOT NULL " +
                "order by t1.created DESC";

        return find.setRawSql(RawSqlBuilder.parse(sql).create())
                .setFirstRow(from)
                .setMaxRows(size)
                .findList();
    }

    public static int getNotificationsCount(User user) {
        String sql = "select t1.id " +
                "from n4user t0 " +
                "left outer join notification_event_n4user t1z_ on t1z_.n4user_id = t0.id " +
                "left outer join notification_event t1 on t1.id = t1z_.notification_event_id " +
                "left outer join notification_mail t2 on t2.notification_event_id = t1.id " +
                "where t0.id = " + user.id + " and t1.id IS NOT NULL ";

        return find.setRawSql(RawSqlBuilder.parse(sql).create()).findList().size();
    }

    public static void afterCommentUpdated(Comment comment) {
        webhookRequest(COMMENT_UPDATED, comment, false);
        NotificationEvent.add(forUpdatedComment(comment, UserApp.currentUser()));
    }

    @Override
    public String toString() {
        return "NotificationEvent{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", senderId=" + senderId +
                ", receivers=" + receivers +
                ", created=" + created +
                ", resourceType=" + resourceType +
                ", resourceId='" + resourceId + '\'' +
                ", eventType=" + eventType +
                ", oldValue='" + oldValue + '\'' +
                ", newValue='" + newValue + '\'' +
                ", notificationMail=" + notificationMail +
                '}';
    }
}
