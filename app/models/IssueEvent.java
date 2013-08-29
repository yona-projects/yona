package models;

import controllers.UserApp;
import models.enumeration.EventType;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import play.Configuration;
import play.db.ebean.Model;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import java.util.Date;

@Entity
public class IssueEvent extends Model implements TimelineItem {
    @Id
    public Long id;

    public Date created;

    public String senderLoginId;

    @ManyToOne
    public Issue issue;

    public EventType eventType;

    public String oldValue;

    public String newValue;

    private static final int DRAFT_TIME_IN_MILLIS = Configuration.root()
        .getMilliseconds("application.issue-event.draft-time", 30 * 1000L).intValue();

    public static Finder<Long, IssueEvent> find = new Finder<Long, IssueEvent>(Long.class,
            IssueEvent.class);

    /**
     * {@code event}를 추가한다.
     *
     * 직전에 추가된 이벤트가 현재 추가하려고 하는 이벤트가 같은 종류이며, 직전 이벤트가 추가된 지
     * {@link #DRAFT_TIME_IN_MILLIS}밀리초가 지나지 않았다면 불필요한 이벤트를 최대한 줄이기 위해, 이전
     * 이벤트와 이 이벤트를 합치거나 상황에 따라서는 두 이벤트를 모두 삭제한다.
     *
     * - 대체되는 경우의 예: 담당자가 A에서 B로 바뀐 뒤, 다시 B에서 C로 바뀌었다면,
     *                       두 이벤트는 담당자가 A에서 C로 바뀐 이벤트로 합쳐진다.
     * - 삭제되는 경우의 예: 담당자가 A에서 B로 바뀐 뒤, 다시 B에서 A로 바뀌었다면, 두 이벤트는 삭제된다.
     *
     * 비고: 이 메소드는 {@link NotificationEvent#add(NotificationEvent)}를 가져와서 필요에 따라 약간
     * 수정한 것이다.
     *
     * @param event
     */
    public static void add(IssueEvent event) {
        Date draftDate = DateTime.now().minusMillis(DRAFT_TIME_IN_MILLIS).toDate();

        IssueEvent lastEvent = IssueEvent.find.where()
                .eq("issue.id", event.issue.id)
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

    /**
     * 주어진 {@code notiEvent}, {@code updatedIssue}, {@code senderLoginId}를 바탕으로 새로운 이슈
     * 이벤트를 만들어 추가한다.
     *
     * @param notiEvent
     * @param updatedIssue
     * @param senderLoginId
     * @see {@link #add(IssueEvent)}
     */
    public static void addFromNotificationEvent(NotificationEvent notiEvent, Issue updatedIssue,
                                                String senderLoginId) {
        IssueEvent event = new IssueEvent();
        event.created = notiEvent.created;
        event.senderLoginId = senderLoginId;
        event.issue = updatedIssue;
        event.eventType = notiEvent.eventType;
        event.oldValue = notiEvent.oldValue;
        event.newValue = notiEvent.newValue;
        add(event);
    }

    @Override
    public Date getDate() {
        return created;
    }
}
