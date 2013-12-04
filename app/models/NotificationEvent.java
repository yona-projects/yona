package models;

import models.enumeration.EventType;
import models.enumeration.RequestState;
import models.enumeration.ResourceType;
import models.enumeration.State;
import models.resource.GlobalResource;
import models.resource.Resource;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;

import controllers.UserApp;
import controllers.routes;
import play.Configuration;
import play.api.mvc.Call;
import play.db.ebean.Model;
import play.i18n.Messages;
import play.mvc.Http;
import play.mvc.Http.Request;
import playRepository.Commit;
import playRepository.GitConflicts;
import playRepository.GitRepository;
import utils.WatchService;

import javax.persistence.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Entity
public class NotificationEvent extends Model {
    private static final long serialVersionUID = 1L;

    private static final int NOTIFICATION_DRAFT_TIME_IN_MILLIS = Configuration.root()
            .getMilliseconds("application.notification.draft-time", 30 * 1000L).intValue();

    @Id
    public Long id;

    public static Finder<Long, NotificationEvent> find = new Finder<Long,
            NotificationEvent>(Long.class, NotificationEvent.class);

    public String title;

    @Lob
    public String message;

    public Long senderId;

    @ManyToMany(cascade = CascadeType.ALL)
    public Set<User> receivers;

    @Temporal(TemporalType.TIMESTAMP)
    public Date created;

    public String urlToView;

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

    public static String formatReplyTitle(Project project, Commit commit) {
        return String.format("Re: [%s] %s (%s)",
                project.name, commit.getShortMessage(), commit.getShortId());
    }

    public static String formatReplyTitle(PullRequest pullRequest) {
        return String.format("Re: [%s] %s (#%s)",
                pullRequest.toProject.name, pullRequest.title, pullRequest.id);
    }

    public static String formatReplyTitle(AbstractPosting posting) {
        return String.format("Re: [%s] %s (#%d)",
                posting.project.name, posting.title, posting.getNumber());
    }

    public static String formatReplyTitle(Project project, User user) {
        return String.format("Re: [%s] @%s wants to join your project", project.name, user.loginId);
    }

    public static String formatNewTitle(AbstractPosting posting) {
        return String.format("[%s] %s (#%d)",
                posting.project.name, posting.title, posting.getNumber());
    }

    public static String formatNewTitle(PullRequest pullRequest) {
        return String.format("[%s] %s (#%d)",
                pullRequest.toProject.name, pullRequest.title, pullRequest.id);
    }

    public static String formatNewTitle(Project project, User user) {
        return String.format("[%s] @%s wants to join your project", project.name, user.loginId);
    }

    public String getOldValue() {
        return oldValue;
    }

    @Transient
    public static Set<User> getMentionedUsers(String body) {
        Matcher matcher = Pattern.compile("@" + User.LOGIN_ID_PATTERN).matcher(body);
        Set<User> users = new HashSet<>();
        while(matcher.find()) {
            users.add(User.findByLoginId(matcher.group().substring(1)));
        }
        users.remove(User.anonymous);
        return users;
    }

    @Transient
    public String getMessage() {
        if (message != null) {
            return message;
        }

        switch (eventType) {
        case ISSUE_STATE_CHANGED:
            if (newValue.equals(State.CLOSED.state())) {
                return Messages.get("notification.issue.closed");
            } else {
                return Messages.get("notification.issue.reopened");
            }
        case ISSUE_ASSIGNEE_CHANGED:
            if (newValue == null) {
                return Messages.get("notification.issue.unassigned");
            } else {
                return Messages.get("notification.issue.assigned", newValue);
            }
        case NEW_ISSUE:
        case NEW_POSTING:
        case NEW_COMMENT:
        case NEW_PULL_REQUEST:
        case NEW_PULL_REQUEST_COMMENT:
            return newValue;
        case PULL_REQUEST_STATE_CHANGED:
            if (State.OPEN.state().equals(newValue)) {
                return Messages.get("notification.pullrequest.reopened");
            } else {
                return Messages.get("notification.pullrequest." + newValue);
            }
        case PULL_REQUEST_COMMIT_CHANGED:
            return newValue;
        case PULL_REQUEST_MERGED:
            return Messages.get("notification.type.pullrequest.merged." + newValue) + "\n" + StringUtils.defaultString(oldValue, StringUtils.EMPTY);
        case MEMBER_ENROLL_REQUEST:
            if (RequestState.REQUEST.name().equals(newValue)) {
                return Messages.get("notification.member.enroll.request");
            } else  if (RequestState.ACCEPT.name().equals(newValue)) {
                return Messages.get("notification.member.enroll.accept");
            } else {
                return Messages.get("notification.member.enroll.cancel");
            }
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
                if (!WatchService.isWatching(receiver, resource)) {
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
     * 신규로 코드를 보냈을때의 알림 설정
     * @param pullRequestCall
     * @param pullRequest
     * @return
     */
    public static NotificationEvent addNewPullRequest(Call pullRequestCall, Http.Request request, PullRequest pullRequest) {
        String title = NotificationEvent.formatNewTitle(pullRequest);
        Set<User> watchers = pullRequest.getWatchers();
        watchers.addAll(NotificationEvent.getMentionedUsers(pullRequest.body));
        watchers.addAll(GitRepository.getRelatedAuthors(pullRequest));
        watchers.remove(pullRequest.contributor);

        NotificationEvent notiEvent = new NotificationEvent();
        notiEvent.created = new Date();
        notiEvent.title = title;
        notiEvent.senderId = UserApp.currentUser().id;
        notiEvent.receivers = watchers;
        notiEvent.urlToView = pullRequestCall.url();
        notiEvent.resourceId = pullRequest.id.toString();
        notiEvent.resourceType = pullRequest.asResource().getType();
        notiEvent.eventType = EventType.NEW_PULL_REQUEST;
        notiEvent.oldValue = null;
        notiEvent.newValue = pullRequest.body;

        NotificationEvent.add(notiEvent);

        return notiEvent;
    }

    /**
     * 보낸코드의 상태가 변경되었을때의 알림 설정
     *
     * @param pullRequestCall
     * @param request
     * @param pullRequest
     * @param oldState
     * @param newState
     * @return
     */
    public static NotificationEvent addPullRequestUpdate(Call pullRequestCall, Http.Request request, PullRequest pullRequest, State oldState, State newState) {
        String title = NotificationEvent.formatNewTitle(pullRequest);
        Set<User> watchers = pullRequest.getWatchers();
        watchers.addAll(NotificationEvent.getMentionedUsers(pullRequest.body));
        watchers.remove(UserApp.currentUser());

        NotificationEvent notiEvent = new NotificationEvent();
        notiEvent.created = new Date();
        notiEvent.title = title;
        notiEvent.senderId = UserApp.currentUser().id;
        notiEvent.receivers = watchers;
        notiEvent.urlToView = pullRequestCall.url();
        notiEvent.resourceId = pullRequest.id.toString();
        notiEvent.resourceType = pullRequest.asResource().getType();
        notiEvent.eventType = EventType.PULL_REQUEST_STATE_CHANGED;
        notiEvent.oldValue = oldState.state();
        notiEvent.newValue = newState.state();

        add(notiEvent);

        return notiEvent;
    }

    /**
     * 보낸코드의 상태가 변경되었을때의 알림 설정
     *
     * @param pullRequestCall
     * @param request
     * @param pullRequest
     * @param oldState
     * @param newState
     * @return
     */
    public static NotificationEvent addPullRequestUpdate(User sender, PullRequest pullRequest, State oldState, State newState) {
        String title = NotificationEvent.formatNewTitle(pullRequest);
        Set<User> watchers = pullRequest.getWatchers();
        watchers.addAll(NotificationEvent.getMentionedUsers(pullRequest.body));
        watchers.remove(sender);
        Project toProject = pullRequest.toProject;

        NotificationEvent notiEvent = new NotificationEvent();
        notiEvent.created = new Date();
        notiEvent.title = title;
        notiEvent.senderId = sender.id;
        notiEvent.receivers = watchers;
        notiEvent.urlToView = routes.PullRequestApp.pullRequest(
                toProject.owner, toProject.name, pullRequest.number).url();
        notiEvent.resourceId = pullRequest.id.toString();
        notiEvent.resourceType = pullRequest.asResource().getType();
        notiEvent.eventType = EventType.PULL_REQUEST_STATE_CHANGED;
        notiEvent.oldValue = oldState.state();
        notiEvent.newValue = newState.state();

        add(notiEvent);

        return notiEvent;
    }
    /**
     * 보낸 코드의 병합 결과 알림 설정
     * @param sender
     * @param pullRequest
     * @param conflicts
     * @param request
     * @param state
     * @return
     */
    public static NotificationEvent addPullRequestMerge(User sender, PullRequest pullRequest, GitConflicts conflicts, Request request, State state) {

        String title = NotificationEvent.formatReplyTitle(pullRequest);
        Resource resource = pullRequest.asResource();
        Set<User> receivers = new HashSet<>();
        receivers.add(pullRequest.contributor);
        Project toProject = pullRequest.toProject;

        NotificationEvent notiEvent = new NotificationEvent();
        notiEvent.created = new Date();
        notiEvent.title = title;
        notiEvent.senderId = sender.id;
        notiEvent.receivers = receivers;
        notiEvent.urlToView = routes.PullRequestApp.pullRequest(
                toProject.owner, toProject.name, pullRequest.number).url();
        notiEvent.resourceId = resource.getId();
        notiEvent.resourceType = resource.getType();
        notiEvent.eventType = EventType.PULL_REQUEST_MERGED;
        notiEvent.newValue = state.state();

        if (conflicts != null) {
            notiEvent.oldValue = StringUtils.join(conflicts.conflictFiles, "\n");
        }

        add(notiEvent);

        return notiEvent;
    }
}
