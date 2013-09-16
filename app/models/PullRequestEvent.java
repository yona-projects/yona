package models;

import java.util.Date;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;

import models.enumeration.EventType;
import play.Configuration;
import play.db.ebean.Model;

@Entity
public class PullRequestEvent extends Model implements TimelineItem {

    private static final long serialVersionUID = 1981361242582594128L;
    public static Finder<Long, PullRequestEvent> finder = new Finder<Long, PullRequestEvent>(Long.class, PullRequestEvent.class); 
    
    @Id
    public Long id;
    public String senderLoginId;
    
    @ManyToOne
    public PullRequest pullRequest;
    
    @Enumerated(EnumType.STRING)
    public EventType eventType;
    
    public Date created;
    
    public String oldValue;
    public String newValue;
    
    private static final int DRAFT_TIME_IN_MILLIS = Configuration.root()
            .getMilliseconds("application.issue-event.draft-time", 30 * 1000L).intValue();
            
    @Override
    public Date getDate() {
        return created;
    }

    public static void add(PullRequestEvent event) {
        Date draftDate = DateTime.now().minusMillis(DRAFT_TIME_IN_MILLIS).toDate();

        PullRequestEvent lastEvent = PullRequestEvent.finder.where()
                .eq("pullRequest.id", event.pullRequest.id)
                .gt("created", draftDate)
                .orderBy("id desc").setMaxRows(1).findUnique();

        if (lastEvent != null) {
            if (lastEvent.eventType == event.eventType &&
                    StringUtils.equals(event.senderLoginId, lastEvent.senderLoginId)) {
                // A -> B, B -> C ==> A -> C
                event.oldValue = lastEvent.oldValue;
                lastEvent.delete();

                // A -> B, B -> A ==> remove all of them
                if (StringUtils.equals(event.oldValue, event.newValue)) {
                    // No need to add this event because the event just cancels the last event
                    // which has just been deleted.
                    return;
                }
            }
        }

        event.save();
    }
    
    public static void addEvent(NotificationEvent notiEvent, PullRequest pullRequest, String senderLoginId) {
        PullRequestEvent event = new PullRequestEvent();
        event.created = notiEvent.created;
        event.senderLoginId = senderLoginId;
        event.pullRequest = pullRequest;
        event.eventType = notiEvent.eventType;
        event.oldValue = notiEvent.oldValue;
        event.newValue = notiEvent.newValue;
        
        add(event);
    }

    public static List<PullRequestEvent> findByPullRequest(PullRequest pullRequest) {
        return finder.where().eq("pullRequest", pullRequest).findList();
    }
}
