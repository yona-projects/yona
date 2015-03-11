/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Yi EungJun
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package models;

import models.enumeration.EventType;
import models.enumeration.ResourceType;
import org.junit.Test;


import java.util.HashSet;
import java.util.Set;

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

}
