/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/
package models;

import models.enumeration.EventType;
import models.enumeration.ResourceType;
import org.junit.Ignore;
import org.junit.Test;
import play.i18n.Lang;


import java.util.HashSet;
import java.util.Set;

import static org.fest.assertions.Assertions.assertThat;

public class NotificationEventTest extends ModelTest<NotificationEvent> {

    private NotificationEvent getNotificationEvent(ResourceType resourceType) {
        NotificationEvent event = new NotificationEvent();
        event.resourceType = resourceType;
        event.resourceId = "1";
        return event;
    }

    @Ignore("Test is ignored as old test with repository dependency")
    @Test
    public void add() {
        // Given
        NotificationEvent event = getNotificationEvent(ResourceType.ISSUE_POST);

        // When
        NotificationEvent.add(event);

        // Then
        assertThat(NotificationMail.find.byId(event.notificationMail.id)).isNotNull();
    }

    @Ignore("Test is ignored as old test with repository dependency")
    @Test
    public void addTwoTimes() {
        // Given
        NotificationEvent event = getNotificationEvent(ResourceType.ISSUE_POST);
        NotificationEvent.add(event);
        int numOfMails = NotificationMail.find.all().size();

        // When
        NotificationEvent.add(event);

        // Then
        assertThat(NotificationEvent.find.all().size()).isEqualTo(numOfMails);
    }

    @Ignore("Test is ignored as old test with repository dependency")
    @Test
    public void delete() {
        // Given
        NotificationEvent event = getNotificationEvent(ResourceType.ISSUE_POST);
        NotificationEvent.add(event);

        // When
        event.delete();

        // Then
        assertThat(NotificationMail.find.byId(event.notificationMail.id)).isNull();
    }

    @Ignore("Test is ignored as old test with repository dependency")
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

        NotificationEvent event = getNotificationEvent(ResourceType.ISSUE_POST);
        event.eventType = EventType.ISSUE_ASSIGNEE_CHANGED;
        event.receivers.add(watching_project_off);
        event.receivers.add(off);

        // When
        NotificationEvent.add(event);

        // Then
        assertThat(event.receivers).containsOnly(off);
    }

    @Ignore("Test is ignored as old test with repository dependency")
    @Test
    public void getNewMentionedUsers1() {
        // Given
        String loginId = "doortts";
        String oldBody = "I'm @yobi";
        String newBody = "I'm @" + loginId;

        // When
        Set<User> newMentionedUsers = NotificationEvent.getNewMentionedUsers(oldBody, newBody);

        // Then
        User newMentionedUser = User.findByLoginId(loginId);
        assertThat(newMentionedUsers.size() == 1).isTrue();
        assertThat(newMentionedUsers.contains(newMentionedUser)).isTrue();
    }

    @Ignore("Test is ignored as old test with repository dependency")
    @Test
    public void getNewMentionedUsers2() {
        // Given
        String loginId = "laziel";
        String oldBody = "They are @yobi and @doortts";
        String newBody = "They are @" + loginId + " and @unknownUserLoginId";

        // When
        Set<User> newMentionedUsers = NotificationEvent.getNewMentionedUsers(oldBody, newBody);

        // Then
        User newMentionedUser = User.findByLoginId(loginId);
        assertThat(newMentionedUsers.size() == 1).isTrue();
        assertThat(newMentionedUsers.contains(newMentionedUser)).isTrue();
    }

    @Test
    public void getMessage_eventTypeIsIssueBodyChangedWithNoParameter_returnString() {

        // Given
        NotificationEvent notificationEvent = getNotificationEvent(ResourceType.ISSUE_POST);
        notificationEvent.eventType = EventType.ISSUE_BODY_CHANGED;
        notificationEvent.oldValue = "old value";
        notificationEvent.newValue = "new value";

        // When
        String result = notificationEvent.getMessage();

        // Then
        assertThat(result.length() > 0).isTrue();
    }

    @Test
    public void getMessage_eventTypeIsIssueBodyChangedWithParameter_returnString() {

        // Given
        NotificationEvent notificationEvent = getNotificationEvent(ResourceType.ISSUE_POST);
        notificationEvent.eventType = EventType.ISSUE_BODY_CHANGED;
        notificationEvent.oldValue = "old value";
        notificationEvent.newValue = "new value";

        // When
        String result = notificationEvent.getMessage(Lang.defaultLang());

        // Then
        assertThat(result.length() > 0).isTrue();
    }

  @Test
  public void getPlainMessage_eventTypeIsIssueBodyChangedWithNoParameter_returnString() {

    // Given
    NotificationEvent notificationEvent = getNotificationEvent(ResourceType.ISSUE_POST);
    notificationEvent.eventType = EventType.ISSUE_BODY_CHANGED;
    notificationEvent.oldValue = "old value";
    notificationEvent.newValue = "new value";

    // When
    String result = notificationEvent.getPlainMessage();

    // Then
    assertThat(result.length() > 0).isTrue();
  }

  @Test
  public void getPlainMessage_eventTypeIsIssueBodyChangedWithParameter_returnString() {

    // Given
    NotificationEvent notificationEvent = getNotificationEvent(ResourceType.ISSUE_POST);
    notificationEvent.eventType = EventType.ISSUE_BODY_CHANGED;
    notificationEvent.oldValue = "old value";
    notificationEvent.newValue = "new value";

    // When
    String result = notificationEvent.getPlainMessage(Lang.defaultLang());

    // Then
    assertThat(result.length() > 0).isTrue();
  }

}
