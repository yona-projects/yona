package models;

import controllers.UserApp;
import controllers.routes;
import models.enumeration.*;
import models.resource.GlobalResource;
import models.resource.Resource;
import models.resource.ResourceConvertible;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.revwalk.RevCommit;
import org.joda.time.DateTime;
import org.tmatesoft.svn.core.SVNException;
import play.Configuration;
import play.api.i18n.Lang;
import play.api.mvc.Call;
import play.db.ebean.Model;
import play.i18n.Messages;
import play.libs.Akka;
import playRepository.Commit;
import playRepository.GitCommit;
import playRepository.GitConflicts;
import playRepository.RepositoryService;
import scala.concurrent.duration.Duration;
import utils.RouteUtil;

import javax.persistence.*;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static models.enumeration.EventType.*;

@Entity
public class NotificationEvent extends Model {
    private static final long serialVersionUID = 1L;

    private static final int NOTIFICATION_DRAFT_TIME_IN_MILLIS = Configuration.root()
            .getMilliseconds("application.notification.draft-time", 30 * 1000L).intValue();

    private static final int NOTIFICATION_KEEP_TIME_DEFAULT = -1;
    private static final int NOTIFICATION_KEEP_TIME_IN_DAYS = Configuration.root().getInt(
            "application.notification.keep-time", NOTIFICATION_KEEP_TIME_DEFAULT);

    @Id
    public Long id;

    public static Finder<Long, NotificationEvent> find = new Finder<>(Long.class, NotificationEvent.class);

    public String title;

    @Lob
    public String message;

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

    public String getOldValue() {
        return oldValue;
    }

    @Transient
    public String getMessage() {
        return getMessage(Lang.defaultLang());
    }

    @Transient
    public String getMessage(Lang lang) {
        if (message != null) {
            return message;
        }

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
            case NEW_PULL_REQUEST_COMMENT:
            case NEW_COMMIT:
            case ISSUE_BODY_CHANGED:
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
            case PULL_REQUEST_REVIEWED:
                return Messages.get(lang, "notification.pullrequest.reviewed", newValue);
            case PULL_REQUEST_UNREVIEWED:
                return Messages.get(lang, "notification.pullrequest.unreviewed", newValue);
            default:
                return null;
        }
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

    public boolean resourceExists() {
        return Resource.exists(resourceType, resourceId);
    }

    public static void add(NotificationEvent event) {
        if (event.notificationMail == null) {
            event.notificationMail = new NotificationMail();
            event.notificationMail.notificationEvent = event;
        }

        Date draftDate = DateTime.now().minusMillis(NOTIFICATION_DRAFT_TIME_IN_MILLIS).toDate();

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

    /*
     * 특정 알림 유형에 대해 설정을 꺼둔 사용자가 있을 경우 수신인에서 제외
     * 알림의 대상 Resource 가 project 별 on / off 설정이 불가능할 경우 필터링을 하지 않는다.
     */
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
     * 신규로 코드를 보냈을때의 알림을 추가한다.
     *
     * @param pullRequest
     * @return
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


    /**
     * 보낸코드의 상태가 변경되었을때의 알림을 추가한다.
     *
     * @param sender this parameter is nullable, If this parameter is null, UserApp().currentUser() is used.
     * @param pullRequest
     * @param oldState
     * @param newState
     * @return
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

    /**
     * 보낸 코드의 병합 결과 알림을 추가한다.
     *
     * @param sender
     * @param pullRequest
     * @param conflicts
     * @param state
     * @return
     * @see {@link actors.PullRequestActor#processPullRequestMerging(models.PullRequestEventMessage, models.PullRequest)}
     */
    public static NotificationEvent afterMerge(User sender, PullRequest pullRequest, GitConflicts conflicts, State state) {
        NotificationEvent notiEvent = createFrom(sender, pullRequest);
        notiEvent.title = formatReplyTitle(pullRequest);
        notiEvent.receivers = state == State.MERGED ? getReceiversWithRelatedAuthors(sender, pullRequest) : getReceivers(sender, pullRequest);
        notiEvent.eventType = PULL_REQUEST_MERGED;
        notiEvent.newValue = state.state();
        if (conflicts != null) {
            notiEvent.oldValue = StringUtils.join(conflicts.conflictFiles, "\n");
        }
        NotificationEvent.add(notiEvent);
        return notiEvent;
    }

    /**
     * 보낸 코드에 댓글이 달렸을 때 알림을 추가한다.
     *
     * @param sender
     * @param pullRequest
     * @param newComment
     * @see {@link controllers.PullRequestCommentApp#newComment(String, String, Long)}
     */
    public static void afterNewComment(User sender, PullRequest pullRequest, PullRequestComment newComment) {
        NotificationEvent notiEvent = createFrom(sender, newComment);
        notiEvent.title = formatReplyTitle(pullRequest);
        Set<User> receivers = getMentionedUsers(newComment.contents);
        receivers.addAll(getReceivers(sender, pullRequest));
        receivers.remove(User.findByLoginId(newComment.authorLoginId));
        notiEvent.receivers = receivers;
        notiEvent.eventType = NEW_PULL_REQUEST_COMMENT;
        notiEvent.oldValue = null;
        notiEvent.newValue = newComment.contents;

        NotificationEvent.add(notiEvent);
    }

    public static NotificationEvent afterNewPullRequest(PullRequest pullRequest) {
        return afterNewPullRequest(UserApp.currentUser(), pullRequest);
    }

    public static NotificationEvent afterPullRequestUpdated(PullRequest pullRequest, State oldState, State newState) {
        return afterPullRequestUpdated(UserApp.currentUser(), pullRequest, oldState, newState);
    }

    /**
     * 이슈와 게시물에 새 댓글을 달렸을 때 알림을 추가한다.
     *
     * @param comment
     */
    public static void afterNewComment(Comment comment) {
        AbstractPosting post = comment.getParent();

        NotificationEvent notiEvent = createFromCurrentUser(comment);
        notiEvent.title = formatReplyTitle(post);
        Set<User> receivers = getReceivers(post);
        receivers.addAll(getMentionedUsers(comment.contents));
        receivers.remove(UserApp.currentUser());
        notiEvent.receivers = receivers;
        notiEvent.eventType = NEW_COMMENT;
        notiEvent.oldValue = null;
        notiEvent.newValue = comment.contents;

        NotificationEvent.add(notiEvent);
    }

    /**
     * 이슈와 게시물에 새 댓글을 달렸을 때 알림을 추가한다.
     *
     * @param comment
     */
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

    /**
     * 상태 변경에 대한 알림을 추가한다.
     *
     * 등록된 notification은 사이트 메인 페이지를 통해 사용자에게 보여지며 또한
     * {@link models.NotificationMail#startSchedule()} 에 의해 메일로 발송된다.
     *
     * @param oldState
     * @param issue
     */
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

    /**
     * 담당자 변경에 대한 알림을 추가한다.
     *
     * 등록된 notification은 사이트 메인 페이지를 통해 사용자에게 보여지며 또한
     * {@link models.NotificationMail#startSchedule()} 에 의해 메일로 발송된다.
     *
     * @param oldAssignee
     * @param issue
     */
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
        NotificationEvent notiEvent = createFromCurrentUser(issue);
        notiEvent.title = formatNewTitle(issue);
        notiEvent.receivers = getReceivers(issue);
        notiEvent.eventType = NEW_ISSUE;
        notiEvent.oldValue = null;
        notiEvent.newValue = issue.body;

        NotificationEvent.add(notiEvent);
    }

    public static NotificationEvent afterIssueBodyChanged(String oldBody, Issue issue) {
        NotificationEvent notiEvent = createFromCurrentUser(issue);
        notiEvent.title = formatReplyTitle(issue);
        notiEvent.receivers = getReceivers(issue);
        notiEvent.eventType = EventType.ISSUE_BODY_CHANGED;
        notiEvent.oldValue = oldBody;
        notiEvent.newValue = issue.body;

        NotificationEvent.add(notiEvent);

        return notiEvent;
    }

    public static void afterNewPost(Posting post) {
        NotificationEvent notiEvent = createFromCurrentUser(post);
        notiEvent.title = formatNewTitle(post);
        notiEvent.receivers = getReceivers(post);
        notiEvent.eventType = NEW_POSTING;
        notiEvent.oldValue = null;
        notiEvent.newValue = post.body;

        NotificationEvent.add(notiEvent);
    }

    public static void afterNewCommitComment(Project project, CommitComment codeComment) throws IOException, SVNException, ServletException {
        Commit commit = RepositoryService.getRepository(project).getCommit(codeComment.commitId);
        Set<User> watchers = commit.getWatchers(project);
        watchers.addAll(getMentionedUsers(codeComment.contents));
        watchers.remove(UserApp.currentUser());

        NotificationEvent notiEvent = createFromCurrentUser(codeComment);
        notiEvent.title = formatReplyTitle(project, commit);
        notiEvent.receivers = watchers;
        notiEvent.eventType = NEW_COMMENT;
        notiEvent.oldValue = null;
        notiEvent.newValue = codeComment.contents;

        NotificationEvent.add(notiEvent);
    }

    /**
     * 멤버 등록 요청에 관련된 알림을 보낸다.
     *
     * @param project
     * @param user
     * @param state
     */
    public static void afterMemberRequest(Project project, User user, RequestState state) {
        NotificationEvent notiEvent = createFromCurrentUser(project);
        notiEvent.eventType = MEMBER_ENROLL_REQUEST;
        notiEvent.receivers = getReceivers(project);
        notiEvent.newValue = state.name();
        if (state == RequestState.ACCEPT || state == RequestState.REJECT) {
            notiEvent.receivers.remove(UserApp.currentUser());
            notiEvent.receivers.add(user);
        }
        if (state == RequestState.REQUEST) {
            notiEvent.title = formatNewTitle(project, user);
            notiEvent.oldValue = RequestState.CANCEL.name();
        } else {
            notiEvent.title = formatReplyTitle(project, user);
            notiEvent.oldValue = RequestState.REQUEST.name();
        }
        notiEvent.resourceType = project.asResource().getType();
        notiEvent.resourceId = project.asResource().getId();
        NotificationEvent.add(notiEvent);
    }

    /**
     * 새 커밋이 있을 때 알림을 추가한다.
     *
     * @param commits
     * @param refNames
     * @param project
     * @param sender
     * @param title
     * @param watchers
     */
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

    /**
     * 코드 보내기 리뷰 완료 또는 리뷰 완료 취소할 때 알림을 추가한다.
     *
     * @param pullRequest
     * @param eventType
     * @return
     */
    public static NotificationEvent afterReviewed(PullRequest pullRequest, EventType eventType) {
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
        notiEvent.eventType = eventType;
        notiEvent.newValue = reviewer.loginId;

        add(notiEvent);

        return notiEvent;
    }

    private static String newCommitsMessage(List<RevCommit> commits, List<String> refNames, Project project) {
        StringBuilder result = new StringBuilder();

        if(commits.size() > 0) {
            result.append("New Commits: \n");
            for(RevCommit commit : commits) {
                GitCommit gitCommit = new GitCommit(commit);
                result.append(gitCommit.getShortId());
                result.append(" ");
                result.append(gitCommit.getShortMessage());
                result.append("\n");
            }
        }

        if(refNames.size() > 0) {
            result.append("Branches: \n");
            for(String refName: refNames) {
                result.append(refName);
                result.append("\n");
            }
        }

        return result.toString();
    }

    /**
     * 다음 값을 설정한다.
     * - created: 현재 시간
     * - resourceId: {@code rc}의 id
     * - resourceType {@code rc}의 type
     * - senderId: {@code sender}의 id
     *
     * @param sender
     * @param rc
     * @return
     */
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
     * 다음 값을 설정한다.
     * - created: 현재 시간
     * - resourceId: {@code rc}의 id
     * - resourceType {@code rc}의 type* - created
     * - senderId: {@link controllers.UserApp#currentUser()}를 사용해서 sernderId를 설정한다.
     *
     * @param rc
     * @return
     * @see {@link #createFrom(models.User, models.resource.ResourceConvertible)}
     */
    private static NotificationEvent createFromCurrentUser(ResourceConvertible rc) {
        return createFrom(UserApp.currentUser(), rc);
    }

    private static Set<User> getReceivers(AbstractPosting abstractPosting) {
        Set<User> receivers = abstractPosting.getWatchers();
        receivers.addAll(getMentionedUsers(abstractPosting.body));
        receivers.remove(UserApp.currentUser());
        return receivers;
    }

    /**
     * {@code posting}의 번호를 반환하되, 이슈라면 앞에 "#"을 붙인다.
     *
     * @param posting
     * @return 조건에 따라 앞에 "#"이 붙은 번호
     */
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
        Set<User> watchers = getDefaultReceivers(pullRequest);
        watchers.addAll(pullRequest.relatedAuthors);
        watchers.remove(sender);
        return watchers;
    }

    private static String formatNewTitle(PullRequest pullRequest) {
        return String.format("[%s] %s (#%d)",
                pullRequest.toProject.name, pullRequest.title, pullRequest.number);
    }

    private static String formatReplyTitle(PullRequest pullRequest) {
        return String.format("Re: [%s] %s (#%s)",
                pullRequest.toProject.name, pullRequest.title, pullRequest.number);
    }

    /**
     * 멤버 등록 관련 알림을 받을 대상자 추출
     * - 해당 프로젝트의 매니저이면서 지켜보기를 켜둔 사용자들
     *
     * @param project
     * @return
     */
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

    private static String formatNewTitle(Project project, User user) {
        return String.format("[%s] @%s wants to join your project", project.name, user.loginId);
    }

    private static String formatReplyTitle(Project project, User user) {
        return String.format("Re: [%s] @%s wants to join your project", project.name, user.loginId);
    }

    private static Set<User> getMentionedUsers(String body) {
        Matcher matcher = Pattern.compile("@" + User.LOGIN_ID_PATTERN).matcher(body);
        Set<User> users = new HashSet<>();
        while(matcher.find()) {
            users.add(User.findByLoginId(matcher.group().substring(1)));
        }
        users.remove(User.anonymous);
        return users;
    }

    /**
     * 하루에 한번 {@code NOTIFICATION_KEEP_TIME_IN_DAYS} 보다 오래된 알림을 삭제한다.
     * 값이 지정되지 않았거나 양수가 아니라면 동작하지 않는다.
     */
    public static void scheduleDeleteOldNotifications() {
        if (NOTIFICATION_KEEP_TIME_IN_DAYS > 0) {
            Akka.system()
                    .scheduler()
                    .schedule(
                            Duration.create(1, TimeUnit.MINUTES),
                            Duration.create(1, TimeUnit.DAYS),
                            new Runnable() {
                                @Override
                                public void run() {
                                    Date threshold = DateTime.now()
                                            .minusDays(NOTIFICATION_KEEP_TIME_IN_DAYS).toDate();
                                    List<NotificationEvent> olds = find.where()
                                            .lt("created", threshold).findList();
                                    for (NotificationEvent old : olds) {
                                        old.delete();
                                    }
                                }
                            },
                            Akka.system().dispatcher());
        }
    }
}
