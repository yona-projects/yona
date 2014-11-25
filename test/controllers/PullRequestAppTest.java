/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Wansoon Park
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

import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.Status.*;
import static play.test.Helpers.*;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import models.Project;
import models.PullRequest;
import models.PushedBranch;
import models.User;
import models.enumeration.State;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.*;

import org.junit.rules.TestWatcher;
import play.mvc.Result;
import play.test.FakeApplication;
import play.test.Helpers;
import support.ExecutionTimeWatcher;
import playRepository.GitRepository;

public class PullRequestAppTest {
    protected static FakeApplication app;

    private String ownerLoginId;
    private String projectName;
    private Long pullRequestNumber;
    private static PullRequest pullRequest;
    private static boolean isInit = false;

    @Rule
    public TestWatcher watcher = new ExecutionTimeWatcher();

    @BeforeClass
    public static void beforeClass() {
        app = support.Helpers.makeTestApplication();
        Helpers.start(app);
    }

    @Before
    public void before() {
        GitRepository.setRepoPrefix("resources/test/repo/git/");
    }

    @AfterClass
    public static void afterClase(){
        Helpers.stop(app);
    }

    @After
    public void after() {
        support.Files.rm_rf(new File(GitRepository.getRepoPrefix()));
    }

    @Test
    public void testCloseAnonymous() throws Exception {
        initParameters("alecsiel", "sample", 10L);
        Result result = callAction(
                controllers.routes.ref.PullRequestApp.close(ownerLoginId, projectName, pullRequestNumber)
        );

        assertThat(status(result)).isEqualTo(SEE_OTHER);
    }

    @Test
    public void testCloseNotExistProject() throws Exception {
        initParameters("alecsiel", "sample", 10L);
        User currentUser = User.findByLoginId("admin");

        Result result = callAction(
                controllers.routes.ref.PullRequestApp.close(ownerLoginId, projectName, pullRequestNumber),
                fakeRequest("GET", "/" + ownerLoginId + "/" + projectName + "/pullRequest/" + pullRequestNumber)
                .withSession(UserApp.SESSION_USERID, currentUser.id.toString())
              );

        assertThat(status(result)).isEqualTo(FORBIDDEN);
    }

    @Test
    public void testCloseNotExistPullRequest() throws Exception {
        initParameters("yobi", "projectYobi-1", 10L);
        User currentUser = User.findByLoginId("admin");

        Result result = callAction(
                controllers.routes.ref.PullRequestApp.close(ownerLoginId, projectName, pullRequestNumber),
                fakeRequest("GET", "/" + ownerLoginId + "/" + projectName + "/pullRequest/" + pullRequestNumber)
                .withSession(UserApp.SESSION_USERID, currentUser.id.toString())
              );

        assertThat(status(result)).isEqualTo(NOT_FOUND);
    }

    @Test
    public void testClosePullRequest() throws Exception {
        initParameters("yobi", "projectYobi", 1L);
        User currentUser = User.findByLoginId("admin");

        Result result = callAction(
                controllers.routes.ref.PullRequestApp.close(ownerLoginId, projectName, pullRequestNumber),
                fakeRequest("GET", "/" + ownerLoginId + "/" + projectName + "/pullRequest/" + pullRequestNumber)
                        .withSession(UserApp.SESSION_USERID, currentUser.id.toString())
        );

        assertThat(status(result)).isEqualTo(SEE_OTHER);
        assertThat(PullRequest.findById(pullRequestNumber).state).isEqualTo(State.CLOSED);
    }

    @Test
    public void testClosePullRequestNotAllow() throws Exception {
        initParameters("yobi", "projectYobi", 1L);
        User currentUser = User.findByLoginId("alecsiel");
        User projectOwner = User.findByLoginId("yobi");

        callAction(
                controllers.routes.ref.PullRequestApp.open(ownerLoginId, projectName, pullRequestNumber),
                fakeRequest("GET", "/" + ownerLoginId + "/" + projectName + "/pullRequest/" + pullRequestNumber)
                        .withSession(UserApp.SESSION_USERID, projectOwner.id.toString())
        );

        Result result = callAction(
                controllers.routes.ref.PullRequestApp.close(ownerLoginId, projectName, pullRequestNumber),
                fakeRequest("GET", "/" + ownerLoginId + "/" + projectName + "/pullRequest/" + pullRequestNumber)
                .withSession(UserApp.SESSION_USERID, currentUser.id.toString())
              );

        assertThat(status(result)).isEqualTo(FORBIDDEN);
        assertThat(PullRequest.findById(pullRequestNumber).state).isEqualTo(State.OPEN);
    }

    @Test
    public void testOpenAnonymous() throws Exception {
        initParameters("alecsiel", "sample", 10L);
        Result result = callAction(
                controllers.routes.ref.PullRequestApp.open(ownerLoginId, projectName, pullRequestNumber)
        );

        assertThat(status(result)).isEqualTo(SEE_OTHER);
    }

    @Test
    public void testOpenPullRequestBadRequest() throws Exception {
        //Given
        initParameters("yobi", "projectYobi", 1L);
        User currentUser = User.findByLoginId("admin");

        User projectOwner = User.findByLoginId("yobi");

        final String uri = "/" + ownerLoginId + "/" + projectName + "/pullRequest/" + pullRequestNumber;
        callAction(
                controllers.routes.ref.PullRequestApp.open(ownerLoginId, projectName, pullRequestNumber),
                fakeRequest("GET", uri).withSession(UserApp.SESSION_USERID, projectOwner.id.toString())
        );

        //When
        Result result = callAction(
                controllers.routes.ref.PullRequestApp.open(ownerLoginId, projectName, pullRequestNumber),
                fakeRequest("GET", uri).withSession(UserApp.SESSION_USERID, currentUser.id.toString())
              );

        //Then
        assertThat(status(result)).isEqualTo(BAD_REQUEST).describedAs("open already opened");
    }

    @Test
    public void testOpenPullRequest() throws Exception {
        // Given
        initParameters("yobi", "HelloSocialApp", 1L);
        User currentUser = User.findByLoginId("admin");
        Project project = Project.findByOwnerAndProjectName(ownerLoginId, projectName);
        PullRequest pullRequest = PullRequest.findOne(project, pullRequestNumber);
        PushedBranch pushedBranch = new PushedBranch(new Date(), pullRequest.fromBranch,
                pullRequest.fromProject);
        pushedBranch.save();

        // When
        Result result = callAction(
                controllers.routes.ref.PullRequestApp.open(ownerLoginId, projectName, pullRequestNumber),
                fakeRequest("GET", "/" + ownerLoginId + "/" + projectName + "/pullRequest/" + pullRequestNumber)
                .withSession(UserApp.SESSION_USERID, currentUser.id.toString())
              );

        // Then
        assertThat(status(result)).isEqualTo(SEE_OTHER);
        assertThat(PullRequest.findOne(project, pullRequestNumber).state).isEqualTo(State.OPEN);
        assertThat(
                PushedBranch.find.where().eq("project", pullRequest.fromProject)
                        .eq("name", pullRequest.fromBranch).findUnique()).isNull();
    }

    @Test
    public void testAcceptAnonymous() throws Exception {
        initParameters("alecsiel", "sample", 10L);
        Result result = callAction(
                controllers.routes.ref.PullRequestApp.accept(ownerLoginId, projectName, pullRequestNumber)
        );

        assertThat(status(result)).isEqualTo(SEE_OTHER);
    }

    @Test
    public void testNewForkByAdmin() throws Exception {
        initParameters("yobi", "projectYobi", 1L);
        User currentUser = User.findByLoginId("admin");

        Result result = callAction(
                controllers.routes.ref.PullRequestApp.newFork(ownerLoginId, projectName, null),
                fakeRequest("GET", "/" + ownerLoginId + "/" + projectName + "/newFork")
                        .withSession(UserApp.SESSION_USERID, currentUser.id.toString())
        );

        assertThat(status(result)).isEqualTo(OK);
    }

    @Test
    public void testNewForkByMember() throws Exception {
        initParameters("yobi", "projectYobi", 1L);
        User currentUser = User.findByLoginId("yobi");

        Result result = callAction(
                controllers.routes.ref.PullRequestApp.newFork(ownerLoginId, projectName, null),
                fakeRequest("GET", "/" + ownerLoginId + "/" + projectName + "/newFork")
                        .withSession(UserApp.SESSION_USERID, currentUser.id.toString())
        );

        assertThat(status(result)).isEqualTo(OK);

    }

    @Test
    public void testNewForkByNotMember() throws Exception {
        initParameters("yobi", "projectYobi", 1L);
        User currentUser = User.findByLoginId("alecsiel");

        Result result = callAction(
                controllers.routes.ref.PullRequestApp.newFork(ownerLoginId, projectName, null),
                fakeRequest("GET", "/" + ownerLoginId + "/" + projectName + "/newFork")
                        .withSession(UserApp.SESSION_USERID, currentUser.id.toString())
        );

        assertThat(status(result)).isEqualTo(OK);
    }

    @Test
    public void testNewForkPrivateProjectAndNotMember() throws Exception {
        initParameters("laziel", "Jindo", 1L);
        User currentUser = User.findByLoginId("alecsiel");

        Result result = callAction(
                controllers.routes.ref.PullRequestApp.newFork(ownerLoginId, projectName, null),
                fakeRequest("GET", "/" + ownerLoginId + "/" + projectName + "/newFork")
                        .withSession(UserApp.SESSION_USERID, currentUser.id.toString())
        );

        assertThat(status(result)).isEqualTo(FORBIDDEN);
    }

    @Test
    public void testForkAlreadyExistForkProject() throws Exception {
        initParameters("yobi", "projectYobi", 1L);
        User currentUser = User.findByLoginId("yobi");

        Map<String,String> data = new HashMap<>();
        data.put("owner", "yobi");
        data.put("name", "projectYobi-2");
        data.put("projectScope", "PUBLIC");

        Result result = callAction(
                controllers.routes.ref.PullRequestApp.fork(ownerLoginId, projectName),
                fakeRequest("GET", "/" + ownerLoginId + "/" + projectName + "/fork")
                        .withSession(UserApp.SESSION_USERID, currentUser.id.toString())
                        .withFormUrlEncodedBody(data)
        );

        assertThat(status(result)).isEqualTo(OK);
    }

    @Test
    public void testForkSampleName() throws Exception {
        initParameters("yobi", "TestApp", 1L);
        User currentUser = User.findByLoginId("yobi");

        Map<String,String> data = new HashMap<>();
        data.put("name", "HelloSocialApp-1");

        Result result = callAction(
                controllers.routes.ref.PullRequestApp.fork(ownerLoginId, projectName),
                fakeRequest("GET", "/" + ownerLoginId + "/" + projectName + "/fork")
                .withSession(UserApp.SESSION_USERID, currentUser.id.toString())
                .withFormUrlEncodedBody(data)
              );

        assertThat(status(result)).isEqualTo(SEE_OTHER);
    }
    @Test
    public void testNewForkRoute() throws Exception {
        initParameters("yobi", "projectYobi", 1L);
        String url = "/" + ownerLoginId + "/" + projectName + "/newFork";
        User currentUser = User.findByLoginId("yobi");

        Result result = route(
            fakeRequest(GET, url).withSession(UserApp.SESSION_USERID, currentUser.id.toString())
        );
        assertThat(status(result)).isEqualTo(OK);
    }

    @Test
    public void testCloseRoute() throws Exception {
        initParameters("yobi", "projectYobi", 1L);
        String url = "/" + ownerLoginId + "/" + projectName + "/pullRequest/" + pullRequestNumber + "/close";
        User currentUser = User.findByLoginId("yobi");

        Result result = route(
            fakeRequest(POST, url).withSession(UserApp.SESSION_USERID, currentUser.id.toString())
        );
        assertThat(status(result)).isEqualTo(SEE_OTHER);

        Project project = Project.findByOwnerAndProjectName(ownerLoginId, projectName);
        PullRequest pullRequest = PullRequest.findOne(project, pullRequestNumber);

        assertThat(pullRequest.state).isEqualTo(State.CLOSED);
    }

    @Test
    public void testOpenRoute() throws Exception {
        //Given
        initParameters("yobi", "projectYobi", 1L);
        User currentUser = User.findByLoginId("yobi");
        User projectOwner = User.findByLoginId("yobi");

        String url = "/" + ownerLoginId + "/" + projectName + "/pullRequest/" + pullRequestNumber + "/open";
        callAction(
                controllers.routes.ref.PullRequestApp.open(ownerLoginId, projectName, pullRequestNumber),
                fakeRequest("GET", "/" + ownerLoginId + "/" + projectName + "/pullRequest/" + pullRequestNumber)
                        .withSession(UserApp.SESSION_USERID, projectOwner.id.toString())
        );

        //When
        Result result = route(
            fakeRequest(POST, url).withSession(UserApp.SESSION_USERID, currentUser.id.toString())
        );

        //Then
        assertThat(status(result)).isEqualTo(BAD_REQUEST);

        Project project = Project.findByOwnerAndProjectName(ownerLoginId, projectName);
        PullRequest pullRequest = PullRequest.findOne(project, pullRequestNumber);

        assertThat(pullRequest.state).isEqualTo(State.OPEN);
    }

    private void initParameters(String ownerLoginId, String projectName, Long pullRequestNumber)
            throws Exception {
        this.ownerLoginId = ownerLoginId;
        this.projectName = projectName;
        this.pullRequestNumber = pullRequestNumber;
    }

}
