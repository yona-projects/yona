package models;

import models.enumeration.ResourceType;
import models.enumeration.State;
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
}
