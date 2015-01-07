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
package controllers;

import models.*;
import models.enumeration.ProjectScope;
import models.resource.Resource;
import org.junit.*;
import play.mvc.Result;
import play.test.FakeApplication;
import play.test.Helpers;

import java.util.HashMap;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.*;

public class IssueAppTest {
    protected static FakeApplication app;

    private User admin;
    private User manager;
    private User member;
    private User author;
    private User assignee;
    private User nonmember;
    private User anonymous;
    private Issue issue;

    private String projectOwner = "yobi";
    private String projectName = "projectYobi";
    private Project project;

    @BeforeClass
    public static void beforeClass() {
        app = support.Helpers.makeTestApplication();
        Helpers.start(app);
    }

    @Before
    public void before() {

        project = Project.findByOwnerAndProjectName(projectOwner, projectName);
        project.setProjectScope(ProjectScope.PRIVATE);

        admin = User.findByLoginId("admin");
        manager = User.findByLoginId("yobi");
        member = User.findByLoginId("laziel");
        author = User.findByLoginId("nori");
        assignee = User.findByLoginId("alecsiel");
        nonmember = User.findByLoginId("doortts");
        anonymous = new NullUser();

        issue = new Issue();
        issue.setProject(project);
        issue.setTitle("hello");
        issue.setBody("world");
        issue.setAuthor(author);
        issue.setAssignee(Assignee.add(assignee.id, project.id));
        issue.save();
    }

    @AfterClass
    public static void after() {
        Helpers.stop(app);
    }

    @Test
    public void testInit(){
        assertThat(this.admin.isSiteManager()).describedAs("admin is Site Admin.").isTrue();
        assertThat(ProjectUser.isManager(manager.id, project.id)).describedAs("manager is a manager").isTrue();
        assertThat(ProjectUser.isManager(member.id, project.id)).describedAs("member is a manager").isFalse();
        assertThat(ProjectUser.isMember(member.id, project.id)).describedAs("member is a member").isTrue();
        assertThat(ProjectUser.isMember(author.id, project.id)).describedAs("author is a member").isFalse();
        assertThat(project.isPublic()).describedAs("project is public").isFalse();
        assertThat(ProjectUser.isMember(assignee.id, project.id)).describedAs("assignee is a member").isFalse();
    }
    private Result postBy(User user) {
        //Given
        Map<String,String> data = new HashMap<>();
        data.put("title", "hello");
        data.put("body", "world");

        //When
        return callAction(
                controllers.routes.ref.IssueApp.newIssue(projectOwner, projectName),
                fakeRequest(POST, routes.IssueApp.newIssue(projectOwner, projectName).url())
                        .withFormUrlEncodedBody(data).withSession(UserApp.SESSION_USERID,
                                user.getId().toString()));
    }

    private Result editBy(User user) {
        Map<String,String> data = new HashMap<>();
        data.put("title", "bye");
        data.put("body", "universe");

        return callAction(
                controllers.routes.ref.IssueApp.editIssue(projectOwner, projectName,
                        issue.getNumber()),
                fakeRequest(
                        POST,
                        routes.IssueApp.editIssue(projectOwner, projectName, issue.getNumber())
                                .url()).withFormUrlEncodedBody(data).withSession(
                        UserApp.SESSION_USERID, user.id.toString()));
    }

    private Result deleteBy(User user) {
        return callAction(
                controllers.routes.ref.IssueApp.deleteIssue(projectOwner, projectName,
                        issue.getNumber()),
                fakeRequest(
                        DELETE,
                        routes.IssueApp.deleteIssue(projectOwner, projectName, issue.getNumber())
                                .url()).withSession(UserApp.SESSION_USERID, user.id.toString()));
    }

    private Result commentBy(User user) {
        //Given
        Map<String,String> data = new HashMap<>();
        data.put("contents", "world");

        //When
        return callAction(
                controllers.routes.ref.IssueApp.newComment(projectOwner, projectName,
                        issue.getNumber()),
                fakeRequest(
                        POST,
                        routes.IssueApp.newComment(projectOwner, projectName, issue.getNumber())
                                .url()).withFormUrlEncodedBody(data).withSession(
                        UserApp.SESSION_USERID, user.getId().toString()));
    }

    @Test
    public void editByNonmember() {
        // Given
        project.refresh();
        project.setProjectScope(ProjectScope.PUBLIC);
        project.update();

        // When
        Result result = editBy(nonmember);

        // Then
        assertThat(status(result)).describedAs("Nonmember can't edit other's issue.").isEqualTo(FORBIDDEN);
    }


    @Test
    public void editByAuthor() {
        // When
        Result result = editBy(author);

        // Then
        assertThat(status(result)).describedAs("Author can edit own issue.").isEqualTo(SEE_OTHER);
    }

    @Test
    public void editByAssignee() {
        // When
        Result result = editBy(assignee);

        // Then
        assertThat(status(result)).describedAs("Assignee can edit own issue.").isEqualTo(SEE_OTHER);
    }

    @Test
    public void editByAdmin() {
        // When
        Result result = editBy(admin);

        // Then
        assertThat(status(result)).describedAs("Site Admin can edit other's issue.").isEqualTo(SEE_OTHER);
    }

    @Test
    public void editByManager() {
        // When
        Result result = editBy(manager);

        // Then
        assertThat(status(result)).describedAs("Project Manager can edit other's issue.").isEqualTo(SEE_OTHER);
    }

    @Test
    public void editByMember() {
        // When
        Result result = editBy(member);

        // Then
        assertThat(status(result)).describedAs("Member can edit other's issue.").isEqualTo(SEE_OTHER);
    }

    @Test
    public void deleteByNonmember() {
        // Given
        project.refresh();
        project.setProjectScope(ProjectScope.PUBLIC);
        project.update();

        // When
        Result result = deleteBy(nonmember);

        // Then
        assertThat(status(result)).describedAs("Nonmember can't delete other's issue.").isEqualTo(FORBIDDEN);
    }


    @Test
    public void deleteByAuthor() {
        // When
        Result result = deleteBy(author);

        // Then
        assertThat(status(result)).describedAs("Author can delete own issue.").isEqualTo(SEE_OTHER);
    }

    @Test
    public void deleteByAssignee() {
        // When
        Result result = deleteBy(assignee);

        // Then
        assertThat(status(result)).describedAs("Assignee can delete own issue.").isEqualTo(SEE_OTHER);
    }

    @Test
    public void deleteByAdmin() {
        // When
        Result result = deleteBy(admin);

        // Then
        assertThat(status(result)).describedAs("Site Admin can delete other's issue.").isEqualTo(SEE_OTHER);
    }

    @Test
    public void deleteByManager() {
        // When
        Result result = deleteBy(manager);

        // Then
        assertThat(status(result)).describedAs("Project Manager can delete other's issue.").isEqualTo(SEE_OTHER);
    }

    @Test
    public void deleteByMember() {
        // When
        Result result = deleteBy(member);

        // Then
        assertThat(status(result)).describedAs("Member can delete other's issue.").isEqualTo(SEE_OTHER);
    }

    @Test
    public void postByAnonymous() {
        // Given
        project.refresh();
        project.setProjectScope(ProjectScope.PUBLIC);
        project.update();

        // When
        Result result = postBy(anonymous);

        // Then
        assertThat(status(result)).describedAs("Anonymous can't post an issue.").isEqualTo(FORBIDDEN);
    }

    @Test
    public void postByNonmember() {
        // Given
        project.refresh();
        project.setProjectScope(ProjectScope.PUBLIC);
        project.update();

        // When
        Result result = postBy(nonmember);

        // Then
        assertThat(status(result)).describedAs("Nonmember can post an issue to public project.").isEqualTo(SEE_OTHER);
    }

    @Test
    public void postByAdmin() {
        // When
        Result result = postBy(admin);

        // Then
        assertThat(status(result)).describedAs("Site Admin can post an issue.").isEqualTo(SEE_OTHER);
    }

    @Test
    public void postByManager() {
        // When
        Result result = postBy(manager);

        // Then
        assertThat(status(result)).describedAs("Project Manager can post an issue.").isEqualTo(SEE_OTHER);
    }

    @Test
    public void postByMember() {
        // When
        Result result = postBy(member);

        // Then
        assertThat(status(result)).describedAs("Member can post an issue.").isEqualTo(SEE_OTHER);
    }

    @Test
    public void commentByAnonymous() {
        // Given
        project.refresh();
        project.setProjectScope(ProjectScope.PUBLIC);
        project.update();

        // When
        Result result = commentBy(anonymous);

        // Then
        assertThat(status(result)).describedAs("Anonymous can't comment for an issue.").isEqualTo(FORBIDDEN);
    }

    @Test
    public void commentByNonmember() {
        // Given
        project.refresh();
        project.setProjectScope(ProjectScope.PUBLIC);
        project.update();

        // When
        Result result = commentBy(nonmember);

        // Then
        assertThat(status(result)).describedAs("Nonmember can comment for an issue of public project.").isEqualTo(SEE_OTHER);
    }

    @Test
    public void commentByAdmin() {
        // When
        Result result = commentBy(admin);

        // Then
        assertThat(status(result)).describedAs("Site Admin can comment for an issue.").isEqualTo(SEE_OTHER);
    }

    @Test
    public void commentByManager() {
        // When
        Result result = commentBy(manager);

        // Then
        assertThat(status(result)).describedAs("Project Manager can comment for an issue.").isEqualTo(SEE_OTHER);
    }

    @Test
    public void commentByMember() {
        // When
        Result result = commentBy(member);

        // Then
        assertThat(status(result)).describedAs("Member can comment for an issue.").isEqualTo(SEE_OTHER);
    }

    @Test
    public void watchDefault() {
        issue.refresh();
        assertThat(issue.getWatchers().contains(author))
            .describedAs("The author watches the issue by default.").isTrue();
    }

    @Test
    public void watch() {
        // Given
        Resource resource = issue.asResource();
        project.refresh();
        project.setProjectScope(ProjectScope.PUBLIC);
        project.update();

        // When
        Result result = callAction(
                controllers.routes.ref.WatchApp.watch(resource.asParameter()),
                fakeRequest()
                        .withSession(UserApp.SESSION_USERID, nonmember.id.toString())
        );

        // Then
        issue.refresh();
        assertThat(status(result)).isEqualTo(OK);
        assertThat(issue.getWatchers().contains(nonmember))
            .describedAs("A user becomes a watcher if the user explictly choose to watch the issue.").isTrue();
    }

    @Test
    public void watchByAuthor() {
        // Given
        Resource resource = issue.asResource();

        // When
        Result result = callAction(
                controllers.routes.ref.WatchApp.watch(resource.asParameter()),
                fakeRequest()
                        .withSession(UserApp.SESSION_USERID, author.id.toString())
        );

        // Then
        issue.refresh();
        assertThat(status(result)).isEqualTo(OK);
    }

    @Test
    public void watchByAssignee() {
        // Given
        Resource resource = issue.asResource();

        // When
        Result result = callAction(
                controllers.routes.ref.WatchApp.watch(resource.asParameter()),
                fakeRequest()
                        .withSession(UserApp.SESSION_USERID, assignee.id.toString())
        );

        // Then
        issue.refresh();
        assertThat(status(result)).isEqualTo(OK);
    }

    @Test
    public void unwatch() {
        // Given
        Resource resource = issue.asResource();
        project.refresh();
        project.setProjectScope(ProjectScope.PUBLIC);
        project.update();


        // When
        Result result = callAction(
                controllers.routes.ref.WatchApp.unwatch(resource.asParameter()),
                fakeRequest()
                        .withSession(UserApp.SESSION_USERID, nonmember.id.toString())
        );

        // Then
        issue.refresh();
        assertThat(status(result)).isEqualTo(SEE_OTHER);
        assertThat(issue.getWatchers().contains(nonmember))
            .describedAs("A user becomes a unwatcher if the user explictly choose not to watch the issue.").isFalse();
    }

    @Test
    public void unwatchByAuthor() {
        // Given
        Resource resource = issue.asResource();

        // When
        Result result = callAction(
                controllers.routes.ref.WatchApp.unwatch(resource.asParameter()),
                fakeRequest()
                        .withSession(UserApp.SESSION_USERID, author.id.toString())
        );

        // Then
        issue.refresh();
        assertThat(status(result)).isEqualTo(SEE_OTHER);
    }

    @Test
    public void unwatchByAssignee() {
        // Given
        Resource resource = issue.asResource();

        // When
        Result result = callAction(
                controllers.routes.ref.WatchApp.unwatch(resource.asParameter()),
                fakeRequest()
                        .withSession(UserApp.SESSION_USERID, assignee.id.toString())
        );

        // Then
        issue.refresh();
        assertThat(status(result)).isEqualTo(SEE_OTHER);
    }

}
