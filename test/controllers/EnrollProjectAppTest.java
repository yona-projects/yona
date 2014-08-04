/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author kjkmadness
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

import org.junit.*;

import play.mvc.Http;
import play.mvc.Result;
import play.test.FakeApplication;

public class EnrollProjectAppTest {
    private static FakeApplication application;

    @BeforeClass
    public static void beforeClass() {
        application = support.Helpers.makeTestApplication();
        start(application);
    }

    @AfterClass
    public static void afterClass() {
        stop(application);
    }

    @Test
    public void enrollProjectNotExist() {
        // Given
        String loginId = "no";
        String projectName = "project";

        // When
        Result result = callAction(routes.ref.EnrollProjectApp.enroll(loginId, projectName),
                fakeRequest(POST, routes.EnrollProjectApp.enroll(loginId, projectName).url()));

        // Then
        assertThat(status(result)).isEqualTo(Http.Status.FORBIDDEN);
    }

    @Test
    public void enrollNotGuest() {
        // Given
        Project project = Project.find.byId(1L);
        User admin = User.find.byId(1L);

        // When
        Result result = callAction(
                routes.ref.EnrollProjectApp.enroll(project.owner, project.name),
                fakeRequest(POST, routes.EnrollProjectApp.enroll(project.owner, project.name).url())
                        .withSession(UserApp.SESSION_USERID, admin.id.toString()));

        // Then
        assertThat(status(result)).isEqualTo(Http.Status.BAD_REQUEST);
    }

    @Test
    public void enroll() {
        // Given
        Project project = Project.find.byId(1L);
        User user = User.find.byId(6L);

        // When
        Result result = callAction(
                routes.ref.EnrollProjectApp.enroll(project.owner, project.name),
                fakeRequest(POST, routes.EnrollProjectApp.enroll(project.owner, project.name).url())
                        .withSession(UserApp.SESSION_USERID, user.id.toString()));

        // Then
        assertThat(status(result)).isEqualTo(Http.Status.OK);
        assertThat(user.enrolledProjects).contains(project);
    }

    @Test
    public void enrollDuplicated() {
        // Given
        Project project = Project.find.byId(1L);
        User user = User.find.byId(6L);

        // When
        callAction(
                routes.ref.EnrollProjectApp.enroll(project.owner, project.name),
                fakeRequest(POST, routes.EnrollProjectApp.enroll(project.owner, project.name).url())
                        .withSession(UserApp.SESSION_USERID, user.id.toString()));
        Result result = callAction(
                routes.ref.EnrollProjectApp.enroll(project.owner, project.name),
                fakeRequest(POST, routes.EnrollProjectApp.enroll(project.owner, project.name).url())
                        .withSession(UserApp.SESSION_USERID, user.id.toString()));

        // Then
        assertThat(status(result)).isEqualTo(Http.Status.OK);
        assertThat(user.enrolledProjects).contains(project);
    }

    @Test
    public void cancelEnrollProjectNotExist() {
        // Given
        String loginId = "no";
        String projectName = "project";

        // When
        Result result = callAction(routes.ref.EnrollProjectApp.cancelEnroll(loginId, projectName),
                fakeRequest(POST, routes.EnrollProjectApp.cancelEnroll(loginId, projectName).url()));

        // Then
        assertThat(status(result)).isEqualTo(Http.Status.FORBIDDEN);
    }

    @Test
    public void cancelEnrollNotGuest() {
        // Given
        Project project = Project.find.byId(1L);
        User admin = User.find.byId(1L);

        // When
        Result result = callAction(
                routes.ref.EnrollProjectApp.cancelEnroll(project.owner, project.name),
                fakeRequest(POST,
                        routes.EnrollProjectApp.cancelEnroll(project.owner, project.name).url())
                        .withSession(UserApp.SESSION_USERID, admin.id.toString()));

        // Then
        assertThat(status(result)).isEqualTo(Http.Status.BAD_REQUEST);
    }

    @Test
    public void cancelEnroll() {
        // Given
        Project project = Project.find.byId(1L);
        User user = User.find.byId(6L);

        // When
        Result result = callAction(
                routes.ref.EnrollProjectApp.cancelEnroll(project.owner, project.name),
                fakeRequest(POST,
                        routes.EnrollProjectApp.cancelEnroll(project.owner, project.name).url())
                        .withSession(UserApp.SESSION_USERID, user.id.toString()));

        // Then
        assertThat(status(result)).isEqualTo(Http.Status.OK);
        assertThat(user.enrolledProjects).isEmpty();
    }
}
