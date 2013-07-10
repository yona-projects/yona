package models;

import models.enumeration.NotificationType;
import models.enumeration.ResourceType;
import play.db.ebean.Model;

import javax.persistence.*;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
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
    @Id
    public Long id;

    public static Finder<Long, NotificationEvent> find = new Finder<Long,
            NotificationEvent>(Long.class, NotificationEvent.class);

    public String title;
    public String message;

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

    public String oldValue;

    public String newValue;

    @OneToOne(mappedBy="notificationEvent", cascade = CascadeType.ALL)
    public NotificationMail notificationMail;

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
                if (EnumSet.allOf(ResourceType.class).contains(resourceType)) {
                    play.Logger.warn("Unsupported resource type " + resourceType);
                } else {
                    play.Logger.warn("Unknown resource type " + resourceType);
                }
        }

        return finder.byId(resourceId) != null;
    }

    @Override
    public void save() {
        if (notificationMail == null) {
            notificationMail = new NotificationMail();
            notificationMail.notificationEvent = this;
        }
        super.save();
    }
}
