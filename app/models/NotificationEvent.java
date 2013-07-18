package models;

import models.enumeration.NotificationType;
import models.enumeration.ResourceType;
import models.enumeration.State;
import models.resource.Resource;
import play.db.ebean.Model;
import play.i18n.Messages;

import javax.persistence.*;
import java.util.Date;
import java.util.EnumSet;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: nori
 * Date: 13. 7. 3
 * Time: 오전 11:20
 * To change this template use File | Settings | File Templates.
 */
@Entity
public class NotificationEvent extends Model {
    private static final long serialVersionUID = 1L;

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

    public Long resourceId;

    @Enumerated(EnumType.STRING)
    public NotificationType type;

    @Lob
    public String oldValue;

    @Lob
    public String newValue;

    @OneToOne(mappedBy="notificationEvent", cascade = CascadeType.ALL)
    public NotificationMail notificationMail;

    public static String formatReplyTitle(AbstractPosting posting) {
        return String.format("Re: [%s] %s (#%d)",
                posting.project.name, posting.title, posting.getNumber());
    }

    public static String formatNewTitle(AbstractPosting posting) {
        return String.format("[%s] %s (#%d)",
                posting.project.name, posting.title, posting.getNumber());
    }

    @Transient
    public String getMessage() {
        if (message != null) {
            return message;
        }

        switch (type) {
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
            return newValue;
        default:
            return null;
        }
    }

    public Resource getResource() {
        Resource resource = null;

        switch(resourceType) {
            case ISSUE_POST:
                resource = Issue.finder.byId(resourceId).asResource();
                break;
            case ISSUE_COMMENT:
                resource = IssueComment.find.byId(resourceId).asResource();
                break;
            case NONISSUE_COMMENT:
                resource = PostingComment.find.byId(resourceId).asResource();
                break;
            case LABEL:
                resource = Label.find.byId(resourceId).asResource();
                break;
            case BOARD_POST:
                resource = Posting.finder.byId(resourceId).asResource();
                break;
            case USER:
                resource = null;
                break;
            case PROJECT:
                resource = Project.find.byId(resourceId).asResource();
                break;
            case ATTACHMENT:
                resource = Attachment.find.byId(resourceId).asResource();
                break;
            case MILESTONE:
                resource = Milestone.find.byId(resourceId).asResource();
                break;
            default:
                throw new IllegalArgumentException(getInvalidResourceTypeMessage(resourceType));
        }

        return resource;
    }

    private static String getInvalidResourceTypeMessage(ResourceType resourceType) {
        if (EnumSet.allOf(ResourceType.class).contains(resourceType)) {
            return "Unsupported resource type " + resourceType;
        } else {
            return "Unknown resource type " + resourceType;
        }
    }

    public Project getProject() {
        switch(resourceType) {
        case ISSUE_ASSIGNEE:
            return Assignee.finder.byId(resourceId).project;
        default:
            Resource resource = getResource();
            if (resource != null) {
                return resource.getProject();
            } else {
                return null;
            }
        }
    }

    public boolean resourceExists() {
        Finder<Long, ? extends Model> finder = null;

        switch(resourceType) {
            case ISSUE_POST:
                finder = Issue.finder;
                break;
            case ISSUE_ASSIGNEE:
                finder = Assignee.finder;
                break;
            case ISSUE_COMMENT:
                finder = IssueComment.find;
                break;
            case NONISSUE_COMMENT:
                finder = PostingComment.find;
                break;
            case LABEL:
                finder = Label.find;
                break;
            case BOARD_POST:
                finder = Posting.finder;
                break;
            case USER:
                finder = User.find;
                break;
            case PROJECT:
                finder = Project.find;
                break;
            case ATTACHMENT:
                finder = Attachment.find;
                break;
            case MILESTONE:
                finder = Milestone.find;
                break;
            default:
                throw new IllegalArgumentException(getInvalidResourceTypeMessage(resourceType));
        }

        return finder.byId(resourceId) != null;
    }

    public static void add(NotificationEvent event) {
        if (event.notificationMail == null) {
            event.notificationMail = new NotificationMail();
            event.notificationMail.notificationEvent = event;
        }
        event.save();
    }
}
