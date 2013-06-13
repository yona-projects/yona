package controllers;

import static org.fest.assertions.Assertions.*;
import static play.test.Helpers.*;

import models.Project;
import models.User;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import play.mvc.Http;
import play.mvc.Result;
import play.test.FakeApplication;
import play.test.FakeRequest;

/**
 * WatchProjectAppTest
 * 
 * @author kjkmadness
 */
public class WatchProjectAppTest {
    private static FakeApplication application;

    @BeforeClass
    public static void setUpBeforeClass() {
        application = fakeApplication(inMemoryDatabase());
        start(application);
    }

    @AfterClass
    public static void tearDownAfterClass() {
        stop(application);
        application = null;
    }

    @Test
    public void testWatch_NoProject() {
        // given
        String ownerName = "111";
        String projectName = "222";

        // when
        Result result = callAction(routes.ref.WatchProjectApp.watch(ownerName, projectName));

        //then
        assertThat(status(result)).isEqualTo(Http.Status.BAD_REQUEST);
    }

    @Test
    public void testWatch_AnonymousUser() {
        // given
        String ownerName = "hobi";
        String projectName = "nForge4java";
        FakeRequest request = fakeRequest();

        // when
        Result result = callAction(routes.ref.WatchProjectApp.watch(ownerName, projectName), request);

        // then
        assertThat(status(result)).isEqualTo(Http.Status.SEE_OTHER);
        assertThat(redirectLocation(result)).isEqualTo(routes.UserApp.loginForm().url());
    }

    @Test
    public void testWatch() {
        // given
        String ownerName = "hobi";
        String projectName = "nForge4java";
        String referer = "http://test/projects";
        Long userId = 3L;
        FakeRequest request = fakeRequest().
                withSession(UserApp.SESSION_USERID, String.valueOf(userId)).
                withHeader(Http.HeaderNames.REFERER, referer);

        // when
        Result result = callAction(routes.ref.WatchProjectApp.watch(ownerName, projectName), request);

        // then
        assertThat(status(result)).isEqualTo(Http.Status.SEE_OTHER);
        assertThat(redirectLocation(result)).isEqualTo(referer);
        assertProjectIsInUserWatchingProjects(ownerName, projectName, userId);
    }

    @Test
    public void testUnwatch_NoProject() {
        // given
        String ownerName = "111";
        String projectName = "222";

        // when
        Result result = callAction(routes.ref.WatchProjectApp.unwatch(ownerName, projectName));

        //then
        assertThat(status(result)).isEqualTo(Http.Status.BAD_REQUEST);
    }
    
    @Test
    public void testUnwatch_AnonymousUser() {
        // given
        String ownerName = "hobi";
        String projectName = "nForge4java";
        FakeRequest request = fakeRequest();

        // when
        Result result = callAction(routes.ref.WatchProjectApp.unwatch(ownerName, projectName), request);

        // then
        assertThat(status(result)).isEqualTo(Http.Status.SEE_OTHER);
        assertThat(redirectLocation(result)).isEqualTo(routes.UserApp.loginForm().url());
    }

    @Test
    public void testUnwatch() {
     // given
        String ownerName = "hobi";
        String projectName = "nForge4java";
        String referer = "http://test/projects";
        Long userId = 3L;
        FakeRequest request = fakeRequest().
                withSession(UserApp.SESSION_USERID, String.valueOf(userId)).
                withHeader(Http.HeaderNames.REFERER, referer);

        // when
        Result result = callAction(routes.ref.WatchProjectApp.unwatch(ownerName, projectName), request);

        // then
        assertThat(status(result)).isEqualTo(Http.Status.SEE_OTHER);
        assertThat(redirectLocation(result)).isEqualTo(referer);
        assertProjectIsNotInUserWatchingProjects(ownerName,
                projectName, userId);
    }

    private void assertProjectIsInUserWatchingProjects(String ownerName,
            String projectName, Long userId) {
        assertThat(Project.findByOwnerAndProjectName(ownerName, projectName)).
        isIn(User.find.byId(userId).watchingProjects);
    }

    private void assertProjectIsNotInUserWatchingProjects(String ownerName,
            String projectName, Long userId) {
        assertThat(Project.findByOwnerAndProjectName(ownerName, projectName)).
        isNotIn(User.find.byId(userId).watchingProjects);
    }
}