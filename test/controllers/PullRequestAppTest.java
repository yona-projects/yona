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
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import play.mvc.Result;
import play.test.FakeApplication;
import play.test.Helpers;
import playRepository.GitRepository;

public class PullRequestAppTest {
    protected FakeApplication app;

    private String ownerLoginId;
    private String projectName;
    private Long pullRequestNumber;

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
        initParameters("yobi", "projectYobi", 1L);
        User currentUser = User.findByLoginId("admin");

        Result result = callAction(
                controllers.routes.ref.PullRequestApp.open(ownerLoginId, projectName, pullRequestNumber),
                fakeRequest("GET", "/" + ownerLoginId + "/" + projectName + "/pullRequest/" + pullRequestNumber)
                .withSession(UserApp.SESSION_USERID, currentUser.id.toString())
              );

        assertThat(status(result)).isEqualTo(BAD_REQUEST);
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
        initParameters("yobi", "projectYobi", 1L);
        String url = "/" + ownerLoginId + "/" + projectName + "/pullRequest/" + pullRequestNumber + "/open";
        User currentUser = User.findByLoginId("yobi");

        Result result = route(
            fakeRequest(POST, url).withSession(UserApp.SESSION_USERID, currentUser.id.toString())
        );
        assertThat(status(result)).isEqualTo(BAD_REQUEST);

        Project project = Project.findByOwnerAndProjectName(ownerLoginId, projectName);
        PullRequest pullRequest = PullRequest.findOne(project, pullRequestNumber);

        assertThat(pullRequest.state).isEqualTo(State.OPEN);
    }

    @BeforeClass
    public static void beforeClass() {
        callAction(
                routes.ref.Application.init()
        );
    }

    @Before
    public void before() {
        GitRepository.setRepoPrefix("resources/test/repo/git/");
        GitRepository.setRepoForMergingPrefix("resources/test/repo/git-merging/");
        app = support.Helpers.makeTestApplication();
        Helpers.start(app);
    }

    @After
    public void after() {
        Helpers.stop(app);
        support.Files.rm_rf(new File(GitRepository.getRepoPrefix()));
        support.Files.rm_rf(new File(GitRepository.getRepoForMergingPrefix()));
    }

    private void initParameters(String ownerLoginId, String projectName, Long pullRequestNumber)
            throws Exception {
        this.ownerLoginId = ownerLoginId;
        this.projectName = projectName;
        this.pullRequestNumber = pullRequestNumber;
        Project project = Project.findByOwnerAndProjectName(ownerLoginId, projectName);
        PullRequest pullRequest = PullRequest.findOne(project, pullRequestNumber);
        initRepositories(pullRequest);
    }

    private void initRepositories(PullRequest pullRequest) throws Exception {
        if (pullRequest == null) {
            return;
        }
        initRepository(pullRequest.toProject,
                StringUtils.removeStart(pullRequest.toBranch, "refs/heads/"), "1.txt");
        initRepository(pullRequest.fromProject,
                StringUtils.removeStart(pullRequest.fromBranch, "refs/heads/"), "2.txt");
    }

    private void initRepository(Project project, String branchName, String fileName) throws Exception {
        GitRepository gitRepository = new GitRepository(project);
        gitRepository.create();

        Repository repository = GitRepository.buildMergingRepository(project);
        Git git =  new Git(repository);

        FileUtils.touch(new File(GitRepository.getDirectoryForMerging(project.owner, project.name + "/" + fileName)));
        git.add().addFilepattern(fileName).call();
        git.commit().setMessage(fileName).call();
        git.push().setRefSpecs(new RefSpec("master:master"), new RefSpec("master:" + branchName)).call();
        gitRepository.close();
        repository.close();
    }
}
