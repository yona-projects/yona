package models;

import models.enumeration.EventType;
import models.enumeration.ResourceType;
import org.junit.Test;


import java.util.HashSet;

import static org.fest.assertions.Assertions.assertThat;

public class NotificationEventTest extends ModelTest<NotificationEvent> {

    @Test
    public void add() {
        // Given
        NotificationEvent event = getNotificationEvent();

        // When
        NotificationEvent.add(event);

        // Then
        assertThat(NotificationMail.find.byId(event.notificationMail.id)).isNotNull();
    }

    private NotificationEvent getNotificationEvent() {
        NotificationEvent event = new NotificationEvent();
        event.resourceType = ResourceType.ISSUE_POST;
        event.resourceId = "1";
        HashSet<User> users = new HashSet<>();
        users.add(User.findByLoginId("yobi"));
        event.receivers = users;
        return event;
    }

    @Test
    public void addTwoTimes() {
        // Given
        NotificationEvent event = getNotificationEvent();
        NotificationEvent.add(event);
        int numOfMails = NotificationMail.find.all().size();

        // When
        NotificationEvent.add(event);

        // Then
        assertThat(NotificationEvent.find.all().size()).isEqualTo(numOfMails);
    }

    @Test
    public void delete() {
        // Given
        NotificationEvent event = getNotificationEvent();
        NotificationEvent.add(event);

        // When
        event.delete();

        // Then
        assertThat(NotificationMail.find.byId(event.notificationMail.id)).isNull();
    }

    @Test
    public void add_with_filter() {
        // Given
        Issue issue = Issue.finder.byId(1L);
        Project project = issue.project;

        User watching_project_off = getTestUser(2L);
        Watch.watch(watching_project_off, project.asResource());
        UserProjectNotification.unwatchExplictly(watching_project_off, project, EventType.ISSUE_ASSIGNEE_CHANGED);

        User off = getTestUser(3L);
        UserProjectNotification.unwatchExplictly(off, project, EventType.ISSUE_ASSIGNEE_CHANGED);

        NotificationEvent event = getNotificationEvent();
        event.eventType = EventType.ISSUE_ASSIGNEE_CHANGED;
        event.receivers.add(watching_project_off);
        event.receivers.add(off);

        // When
        NotificationEvent.add(event);

        // Then
        assertThat(event.receivers).containsOnly(off);
    }
}
