/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

package models;

import models.enumeration.EventType;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import play.Configuration;
import play.db.ebean.Model;

import javax.persistence.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;

@Entity
public class IssueEvent extends Model implements TimelineItem {
    private static final long serialVersionUID = 4029013291153199185L;

    @Id
    public Long id;

    public Date created;

    public String senderLoginId;
    public String senderEmail;

    @ManyToOne
    public Issue issue;

    @Enumerated(EnumType.STRING)
    public EventType eventType;

    @Lob
    public String oldValue;

    @Lob
    public String newValue;

    private static final int DRAFT_TIME_IN_MILLIS = Configuration.root()
        .getMilliseconds("application.issue-event.draft-time", 30 * 1000L).intValue();

    public static final Finder<Long, IssueEvent> find = new Finder<>(Long.class,
            IssueEvent.class);

    /**
     * Adds {@code event}.
     *
     * If the last event is not older than {@link #DRAFT_TIME_IN_MILLIS}
     * miliseconds and the event is the same kind of event as the given one,
     * merge or delete both of the events if necessary to reduce hassle
     * notifications.
     *
     * Examples:
     *
     * - If an assignee was changed from A to B, then A to C, the two events
     *   are merged into the event of which assignee was changed from A to C.
     * - If an assignee was changed from A to B, then B to A, the two events
     *   will be deleted.
     *
     * Notes: This method originates from
     * {@link NotificationEvent#add(NotificationEvent)}
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
            if (isSameUserEventAsPrevious(event, lastEvent)) {
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
     * It is nearly same as {@link #add(IssueEvent)} except that it doesn't skip waypoint events.
     *
     * For example, if events of an issue are occurred continuously
     * A -> B -> C, then {@link #add(IssueEvent)} method skip B.
     *
     * This method doesn't skip B and leave it.
     *
     * @param event
     */
    public static void addWithoutSkipEvent(IssueEvent event) {
        Date draftDate = DateTime.now().minusMillis(DRAFT_TIME_IN_MILLIS).toDate();

        IssueEvent lastEvent = IssueEvent.find.where()
                .eq("issue.id", event.issue.id)
                .gt("created", draftDate)
                .orderBy("id desc").setMaxRows(1).findUnique();

        if (lastEvent != null) {
            if (isSameUserEventAsPrevious(event, lastEvent) &&
                    isRevertingTheValue(event, lastEvent)) {
                lastEvent.delete();
                return;
            }
        }
        event.save();
    }

    private static boolean isRevertingTheValue(IssueEvent event, IssueEvent lastEvent) {
        return StringUtils.equals(event.oldValue, lastEvent.newValue) &&
                StringUtils.equals(event.newValue, lastEvent.oldValue);
    }

    private static boolean isSameUserEventAsPrevious(IssueEvent event, IssueEvent lastEvent) {
        return lastEvent.eventType == event.eventType &&
                StringUtils.equals(event.senderLoginId, lastEvent.senderLoginId);
    }

    /**
     * Adds events based on the given {@code notiEvent}, {@code updatedIssue}
     * and {@code senderLoginId}.
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

    public static void addFromNotificationEventWithoutSkipEvent(NotificationEvent notiEvent, Issue updatedIssue,
                                                String senderLoginId) {
        IssueEvent event = new IssueEvent();
        event.created = notiEvent.created;
        event.senderLoginId = senderLoginId;
        event.issue = updatedIssue;
        event.eventType = notiEvent.eventType;
        event.oldValue = notiEvent.oldValue;
        event.newValue = notiEvent.newValue;
        addWithoutSkipEvent(event);
    }

    @Override
    public Date getDate() {
        return created;
    }

    public static Set<Issue> findReferredIssue(String message, Project project) {
        Matcher m = Issue.ISSUE_PATTERN.matcher(message);
        Set<Issue> referredIssues = new HashSet<>();

        while(m.find()) {
            String issueText = m.group();
            String issueNumber = issueText.substring(1); // removing the leading char #
            Issue issue = Issue.findByNumber(project, Long.parseLong(issueNumber));
            if(issue != null) {
                referredIssues.add(issue);
            }
        }

        return referredIssues;
    }
}
