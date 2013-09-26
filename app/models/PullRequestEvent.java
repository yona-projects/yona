package models;

import java.util.ArrayList;
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
import models.enumeration.State;
import play.Configuration;
import play.db.ebean.Model;

@Entity
public class PullRequestEvent extends Model implements TimelineItem {
    
    public static final String COMMIT_ID_DELIMITER = ",";
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
    
    public static void addEvent(NotificationEvent notiEvent, PullRequest pullRequest) {
        PullRequestEvent event = new PullRequestEvent();
        event.created = notiEvent.created;
        event.senderLoginId = notiEvent.getSender().loginId;
        event.pullRequest = pullRequest;
        event.eventType = notiEvent.eventType;
        event.oldValue = notiEvent.oldValue;
        event.newValue = notiEvent.newValue;
        
        add(event);
    }
    
    public static void addMergeEvent(User sender, EventType eventType, State state, PullRequest pullRequest) {
        PullRequestEvent event = new PullRequestEvent();
        event.created = new Date();
        event.senderLoginId = sender.loginId;
        event.pullRequest = pullRequest;
        event.eventType = eventType;
        event.newValue = state.state();
        
        add(event);
    }

    
    public static void addCommitEvents(User sender, PullRequest pullRequest, List<PullRequestCommit> commits) {
        Date createdDate = new Date();
        PullRequestEvent event = new PullRequestEvent();
        event.created = createdDate;
        event.senderLoginId = sender.loginId;
        event.pullRequest = pullRequest;
        event.eventType = EventType.PULL_REQUEST_COMMIT_CHANGED;
        event.newValue = StringUtils.EMPTY;

        for (int i = 0; i < commits.size(); i++) {
            event.newValue += commits.get(i).id;            
            if (i != commits.size() - 1) {
                event.newValue += PullRequestEvent.COMMIT_ID_DELIMITER;
            }
        }
        
        event.save();
    }
 
    public static List<PullRequestEvent> findByPullRequest(PullRequest pullRequest) {
        return finder.where().eq("pullRequest", pullRequest).findList();
    }
    
    public List<PullRequestCommit> getPullRequestCommits() {
        List<PullRequestCommit> commits = new ArrayList<PullRequestCommit>();
        
        String[] commitIds = this.newValue.split(COMMIT_ID_DELIMITER);
        for (String commitId: commitIds) {
            commits.add(PullRequestCommit.findById(commitId));
        }
        
        return commits;
    }
}
