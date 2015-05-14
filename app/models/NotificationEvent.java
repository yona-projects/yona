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
import utils.EventConstants;
import utils.RouteUtil;

import javax.naming.LimitExceededException;
import javax.persistence.*;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    @Lob
    public String oldValue;

    @Lob
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
            case NEW_ISSUE:
            case NEW_POSTING:
            case NEW_COMMENT:
            case NEW_PULL_REQUEST:
            case NEW_COMMIT:
            case ISSUE_BODY_CHANGED:
            case COMMENT_UPDATED:
                return newValue;
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
            default:
                return null;
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
            if (lastEvent.eventType == event.eventType &&
                    event.senderId.equals(lastEvent.senderId)) {
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
        switch(eventType) {
            case MEMBER_ENROLL_REQUEST:
                if (getProject() == null) {
                    return null;
                } else {
                    return routes.ProjectApp.members(
                            getProject().owner, getProject().name).url();
                }
            case ORGANIZATION_MEMBER_ENROLL_REQUEST:
                Organization organization = getOrganization();
                if (organization == null) {
                    return null;
                }
                return routes.OrganizationApp.members(organization.name).url();

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
        return afterNewPullRequest(UserApp.currentUser(), pullRequest);
    }

    public static NotificationEvent afterPullRequestUpdated(PullRequest pullRequest, State oldState, State newState) {
        return afterPullRequestUpdated(UserApp.currentUser(), pullRequest, oldState, newState);
    }

    public static void afterNewComment(Comment comment) {
        NotificationEvent.add(forNewComment(comment, UserApp.currentUser()));
    }

    public static NotificationEvent forComment(Comment comment, User author, EventType eventType) {
        AbstractPosting post = comment.getParent();

        NotificationEvent notiEvent = createFrom(author, comment);
        notiEvent.title = formatReplyTitle(post);
        notiEvent.eventType = eventType;
        Set<User> receivers = getReceivers(post, author);
        receivers.addAll(getMentionedUsers(comment.contents));
        receivers.remove(author);
        notiEvent.receivers = receivers;
        notiEvent.oldValue = null;
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
        NotificationEvent notiEvent = createFromCurrentUser(issue);
        notiEvent.title = formatReplyTitle(issue);
        notiEvent.receivers = getReceivers(issue);
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
        NotificationEvent notiEvent = createFromCurrentUser(issue);

        Set<User> receivers = getReceivers(issue);
        if(oldAssignee != null) {
            notiEvent.oldValue = oldAssignee.loginId;
            if(!oldAssignee.loginId.equals(UserApp.currentUser().loginId)) {
                receivers.add(oldAssignee);
            }
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

    public static void afterNewIssue(Issue issue) {
        NotificationEvent.add(forNewIssue(issue, UserApp.currentUser()));
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

    public static NotificationEvent afterIssueBodyChanged(String oldBody, Issue issue) {
        NotificationEvent notiEvent = createFromCurrentUser(issue);
        notiEvent.title = formatReplyTitle(issue);
        notiEvent.receivers = getReceiversForIssueBodyChanged(oldBody, issue);
        notiEvent.eventType = EventType.ISSUE_BODY_CHANGED;
        notiEvent.oldValue = oldBody;
        notiEvent.newValue = issue.body;

        NotificationEvent.add(notiEvent);

        return notiEvent;
    }

    private static Set<User> getReceiversForIssueBodyChanged(String oldBody, Issue issue) {
        Set<User> receivers = issue.getWatchers();
        receivers.addAll(getNewMentionedUsers(oldBody, issue.body));
        receivers.remove(UserApp.currentUser());
        return receivers;
    }

    public static void afterNewPost(Posting post) {
        NotificationEvent.add(forNewPosting(post, UserApp.currentUser()));
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
                notiEvent.oldValue = RequestState.REQUEST.name();
                break;
        }

        notiEvent.resourceType = organization.asResource().getType();
        notiEvent.resourceId = organization.asResource().getId();
        NotificationEvent.add(notiEvent);
    }

    public static void afterNewCommits(List<RevCommit> commits, List<String> refNames, Project project, User sender, String title, Set<User> watchers) {
        NotificationEvent notiEvent = createFrom(sender, project);
        notiEvent.title = title;
        notiEvent.receivers = watchers;
        notiEvent.eventType = NEW_COMMIT;
        notiEvent.oldValue = null;
        notiEvent.newValue = newCommitsMessage(commits, refNames, project);
        notiEvent.resourceType = project.asResource().getType();
        notiEvent.resourceId = project.asResource().getId();
        NotificationEvent.add(notiEvent);
    }

    public static NotificationEvent afterReviewed(PullRequest pullRequest, PullRequestReviewAction reviewAction) {
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
        NotificationEvent.add(forUpdatedComment(comment, UserApp.currentUser()));
    }
}
