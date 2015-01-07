/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Jungkook Kim
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
package controllers;

import static org.fest.assertions.Assertions.*;
import static play.test.Helpers.*;

import models.Project;
import models.User;

import models.UserProjectNotification;
import models.Watch;
import models.enumeration.EventType;
import models.enumeration.ProjectScope;

import org.junit.*;

import play.mvc.Http;
import play.mvc.Result;
import play.test.FakeApplication;
import play.test.FakeRequest;
import play.test.Helpers;

import java.util.Map;

public class WatchProjectAppTest {
    protected static FakeApplication app;

    @BeforeClass
    public static void beforeClass() {
        app = support.Helpers.makeTestApplication();
        Helpers.start(app);
    }

    @AfterClass
    public static void afterClass() {
        Helpers.stop(app);
    }

    @Test
    public void testWatch_NoProject() {
        // given
        String ownerName = "111";
        String projectName = "222";

        // when
        Result result = callAction(
                    routes.ref.WatchProjectApp.watch(ownerName, projectName),
                    fakeRequest("POST", "/" + ownerName + "/" + projectName + "/watch")
                );

        //then
        assertThat(status(result)).isEqualTo(Http.Status.FORBIDDEN);
    }

    @Test
    public void testWatch_AnonymousUser() {
        // given
        String ownerName = "yobi";
        String projectName = "projectYobi";
        String url = "/" + ownerName + "/" + projectName + "/watch";
        FakeRequest request = fakeRequest("POST", url);

        // when
        Result result = callAction(routes.ref.WatchProjectApp.watch(ownerName, projectName), request);

        // then
        assertThat(status(result)).isEqualTo(Http.Status.SEE_OTHER);
        assertThat(redirectLocation(result)).isEqualTo(routes.UserApp.loginForm().url() + "?redirectUrl=" + url);
    }

    @Test
    public void testWatch() {
        // given
        String ownerName = "yobi";
        String projectName = "projectYobi";
        Long userId = 3L;
        FakeRequest request = fakeRequest("POST", "/" + ownerName + "/" + projectName + "/watch").
                withSession(UserApp.SESSION_USERID, String.valueOf(userId));

        // when
        Result result = callAction(routes.ref.WatchProjectApp.watch(ownerName, projectName), request);

        // then
        assertThat(status(result)).isEqualTo(Http.Status.OK);
        assertProjectIsInUserWatchingProjects(ownerName, projectName, userId);
    }

    @Test
    public void testUnwatch_NoProject() {
        // given
        String ownerName = "111";
        String projectName = "222";

        // when
        Result result = callAction(routes.ref.WatchProjectApp.unwatch(ownerName, projectName),
                fakeRequest("POST", "/" + ownerName + "/" + projectName + "/unwatch")
                );

        //then
        assertThat(status(result)).isEqualTo(Http.Status.FORBIDDEN);
    }

    @Test
    public void testUnwatch_AnonymousUser() {
        // given
        String ownerName = "yobi";
        String projectName = "projectYobi";
        String url = "/" + ownerName + "/" + projectName + "/unwatch";
        FakeRequest request = fakeRequest("POST", url);

        // when
        Result result = callAction(routes.ref.WatchProjectApp.unwatch(ownerName, projectName), request);

        // then
        assertThat(status(result)).isEqualTo(Http.Status.SEE_OTHER);
        assertThat(redirectLocation(result)).isEqualTo(routes.UserApp.loginForm().url() + "?redirectUrl=" + url);
    }

    @Test
    public void testUnwatch() {
     // given
        String ownerName = "yobi";
        String projectName = "projectYobi";
        Long userId = 3L;
        FakeRequest request = fakeRequest("POST", "/" + ownerName + "/" + projectName + "/unwatch").
                withSession(UserApp.SESSION_USERID, String.valueOf(userId));

        // when
        Result result = callAction(routes.ref.WatchProjectApp.unwatch(ownerName, projectName), request);

        // then
        assertThat(status(result)).isEqualTo(Http.Status.OK);
        assertProjectIsNotInUserWatchingProjects(ownerName,
                projectName, userId);
    }

    @Test
    public void testToggleWatchingProject() {
        // given
        Long userId = 1l;
        Long projectId = 1l;
        String eventTypeValue = "NEW_ISSUE";

        User user = User.find.byId(userId);
        Project project = Project.find.byId(projectId);
        EventType eventType = EventType.valueOf(eventTypeValue);

        Watch.watch(user, project.asResource());
        assertThat(user.getWatchingProjects().contains(project)).isTrue();
        assertThat(UserProjectNotification.isEnabledNotiType(user, project, eventType)).isTrue();

        FakeRequest request = fakeRequest("POST", "/noti/toggle/" + projectId + "/" + eventTypeValue)
                .withSession(UserApp.SESSION_USERID, String.valueOf(userId));

        // when
        Result result = callAction(routes.ref.WatchProjectApp.toggle(projectId, eventTypeValue), request);

        // then
        assertThat(status(result)).isEqualTo(Http.Status.OK);
        assertThat(UserProjectNotification.isEnabledNotiType(user, project, eventType)).isFalse();
    }

    @Test
    public void testToggleUnwatchingProject() {
        // given
        Long userId = 1l;
        Long projectId = 2l;
        String eventTypeValue = "NEW_ISSUE";

        User user = User.find.byId(userId);
        Project project = Project.find.byId(projectId);

        assertThat(user.getWatchingProjects().contains(project)).isFalse();

        FakeRequest request = fakeRequest("POST", "/noti/toggle/" + projectId + "/" + eventTypeValue)
                .withSession(UserApp.SESSION_USERID, String.valueOf(userId));

        // when
        Result result = callAction(routes.ref.WatchProjectApp.toggle(projectId, eventTypeValue), request);

        // then
        assertThat(status(result)).isEqualTo(Http.Status.BAD_REQUEST);
    }

    @Test
    public void testToggleNoProject() {
        // given
        Long userId = 1l;
        Long projectId = 100l; // The project which has this id, should not exist.
        String eventTypeValue = "NEW_ISSUE";

        FakeRequest request = fakeRequest("POST", "/noti/toggle/" + projectId + "/" + eventTypeValue)
                .withSession(UserApp.SESSION_USERID, String.valueOf(userId));

        // when
        Result result = callAction(routes.ref.WatchProjectApp.toggle(projectId, eventTypeValue), request);

        // then
        assertThat(status(result)).isEqualTo(Http.Status.NOT_FOUND);
    }

    @Test
    public void testTogglePrivateProject() {
        // given
        Long userId = 2l;
        Long projectId = 3l; // The project should be private.
        String eventTypeValue = "NEW_ISSUE";

        Project project = Project.find.byId(projectId);
        assertThat(project.projectScope).isEqualTo(ProjectScope.PRIVATE);

        FakeRequest request = fakeRequest("POST", "/noti/toggle/" + projectId + "/" + eventTypeValue)
                .withSession(UserApp.SESSION_USERID, String.valueOf(userId));

        // when
        Result result = callAction(routes.ref.WatchProjectApp.toggle(projectId, eventTypeValue), request);

        // then
        assertThat(status(result)).isEqualTo(Http.Status.FORBIDDEN);
    }

    private void assertProjectIsInUserWatchingProjects(String ownerName,
            String projectName, Long userId) {
        assertThat(Project.findByOwnerAndProjectName(ownerName, projectName)).
        isIn(User.find.byId(userId).getWatchingProjects());
    }

    private void assertProjectIsNotInUserWatchingProjects(String ownerName,
            String projectName, Long userId) {
        assertThat(Project.findByOwnerAndProjectName(ownerName, projectName)).
        isNotIn(User.find.byId(userId).getWatchingProjects());
    }
}
