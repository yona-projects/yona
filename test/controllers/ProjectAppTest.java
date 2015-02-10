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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.fest.assertions.Condition;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.tigris.subversion.javahl.ClientException;
import org.tmatesoft.svn.core.SVNException;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import play.test.FakeApplication;
import play.test.Helpers;
import playRepository.GitRepository;
import playRepository.RepositoryService;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.*;

public class ProjectAppTest {
    protected static FakeApplication app;
    private String acceptHtml = "text/html";
    private String acceptJson = "application/json";

    @Before
    public void before() {
        app = support.Helpers.makeTestApplication();
        Helpers.start(app);
    }

    @Test
    public void label() {
        //Given
        Map<String,String> data = new HashMap<>();
        data.put("category", "OS");
        data.put("name", "linux");
        User admin = User.findByLoginId("admin");

        String user = "yobi";
        String projectName = "projectYobi";

        //When
        Result result = callAction(
                controllers.routes.ref.ProjectApp.attachLabel(user, projectName),
                fakeRequest(POST, routes.ProjectApp.attachLabel(user, projectName).url())
                        .withFormUrlEncodedBody(data).withHeader("Accept", "application/json")
                        .withSession(UserApp.SESSION_USERID, admin.id.toString()));

        //Then
        assertThat(status(result)).isEqualTo(CREATED);
        Iterator<Map.Entry<String, JsonNode>> fields = Json.parse(contentAsString(result)).fields();
        Map.Entry<String, JsonNode> field = fields.next();

        Label expected = new Label(field.getValue().get("category").asText(), field.getValue().get("name").asText());
        expected.id = Long.valueOf(field.getKey());

        assertThat(expected.category).isEqualTo("OS");
        assertThat(expected.name).isEqualTo("linux");
        assertThat(Project.findByOwnerAndProjectName("yobi", "projectYobi").labels.contains(expected)).isTrue();
    }

    @Test
    public void labels() {
        //Given
        Project project = Project.findByOwnerAndProjectName("yobi", "projectYobi");

        Label label1 = new Label("OS", "yobi-linux");
        label1.save();
        project.labels.add(label1);
        project.update();

        String user = "yobi";
        String projectName = "projectYobi";

        // If null is given as the first parameter, "Label" is chosen as the category.
        Label label2 = new Label(null, "foo");
        label2.save();
        project.labels.add(label2);
        project.update();

        //When
        Result result = callAction(
                controllers.routes.ref.ProjectApp.labels(user, projectName),
                fakeRequest(GET, routes.ProjectApp.labels(user, projectName).url()).withHeader(
                        "Accept", "application/json"));

        //Then
        assertThat(status(result)).isEqualTo(OK);
        JsonNode json = Json.parse(contentAsString(result));

        String id1 = label1.id.toString();
        String id2 = label2.id.toString();

        assertThat(json.has(id1)).isTrue();
        assertThat(json.has(id2)).isTrue();
        assertThat(json.get(id1).get("category").asText()).isEqualTo("OS");
        assertThat(json.get(id1).get("name").asText()).isEqualTo("yobi-linux");
        assertThat(json.get(id2).get("category").asText()).isEqualTo("Label");
        assertThat(json.get(id2).get("name").asText()).isEqualTo("foo");
    }

    @Test
    public void detachLabel() {
        //Given
        Project project = Project.findByOwnerAndProjectName("yobi", "projectYobi");

        Label label1 = new Label("OS", "linux");
        label1.save();
        project.labels.add(label1);
        project.update();
        Long labelId = label1.id;

        assertThat(project.labels.contains(label1)).isTrue();

        Map<String,String> data = new HashMap<>();
        data.put("_method", "DELETE");
        User admin = User.findByLoginId("admin");

        String user = "yobi";
        String projectName = "projectYobi";

        //When
        Result result = callAction(
                controllers.routes.ref.ProjectApp.detachLabel(user, projectName, labelId),
                fakeRequest(POST, routes.ProjectApp.detachLabel(user, projectName, labelId).url())
                        .withFormUrlEncodedBody(data).withHeader("Accept", "application/json")
                        .withSession(UserApp.SESSION_USERID, admin.id.toString()));

        //Then
        assertThat(status(result)).isEqualTo(204);
        assertThat(Project.findByOwnerAndProjectName("yobi", "projectYobi").labels.contains(label1)).isFalse();
    }

    @Test
    public void projectOverviewUpdateTest(){
        //Given
        Project project = Project.findByOwnerAndProjectName("yobi", "projectYobi");
        String newDescription = "new overview description";

        ObjectNode requestJson = Json.newObject();
        requestJson.put("overview",newDescription);
        User member = User.findByLoginId("yobi");

        //When
        Result result = callAction(
                controllers.routes.ref.ProjectApp.projectOverviewUpdate("yobi", "projectYobi"),
                fakeRequest(PUT, "/yobi/projectYobi").withJsonBody(requestJson)
                        .withHeader("Accept", "application/json")
                        .withHeader("Content-Type", "application/json")
                        .withSession(UserApp.SESSION_USERID, member.id.toString())
        );

        //Then
        assertThat(status(result)).isEqualTo(200);  //response status code
        assertThat(contentAsString(result)).isEqualTo("{\"overview\":\"new overview description\"}");   //response json body

        project.refresh();
        assertThat(project.overview).isEqualTo(newDescription);  //is model updated
    }

    @Test
    public void deletePushedBranch() {
        //Given
        String loginId = "yobi";
        String projectName = "projectYobi";
        Project project = Project.findByOwnerAndProjectName(loginId, projectName);
        PushedBranch pushedBranch = new PushedBranch(new Date(), "testBranch", project);
        pushedBranch.save();
        Long id = pushedBranch.id;

        //When
        Result result = callAction(
                controllers.routes.ref.ProjectApp.deletePushedBranch(project.owner, project.name, id),
                fakeRequest(DELETE, "/yobi/projectYobi/deletePushedBranch/" + id)
                        .withSession(UserApp.SESSION_USERID, User.findByLoginId(loginId).id.toString())
                );

        //Then
        assertThat(status(result)).isEqualTo(OK);
        assertThat(PushedBranch.find.byId(id)).isNull();

    }

    @Test
    public void projectSearchWithNoAcceptHeader() {
        Result result = callAction(routes.ref.ProjectApp.projects("", 1), fakeRequest());
        assertThat(status(result)).isEqualTo(NOT_ACCEPTABLE);
    }

    @Test
    public void adminCanSearchPrivateProjects() {
        // Given
        User admin = User.find.byId(1L);
        Project project = Project.findByOwnerAndProjectName("laziel", "Jindo");

        // When
        // Then
        testProjectSearch(admin, project, acceptHtml, contains(routes.ProjectApp.project(project.owner,project.name).url()));
    }

    @Test
    public void adminCanSearchPrivateProjectsAsJson() {
        // Given
        User admin = User.find.byId(1L);
        Project project = Project.findByOwnerAndProjectName("laziel", "Jindo");

        // When
        // Then
        testProjectSearch(admin, project, acceptJson, contains(project.owner + "/" + project.name));
    }

    @Test
    public void adminCanSearchPublicProjects() {
        // Given
        User admin = User.find.byId(1L);
        Project project = Project.findByOwnerAndProjectName("yobi", "projectYobi");

        // When
        // Then
        testProjectSearch(admin, project, acceptHtml, contains(routes.ProjectApp.project(project.owner,project.name).url()));
    }

    @Test
    public void adminCanSearchPublicProjectsAsJson() {
        // Given
        User admin = User.find.byId(1L);
        Project project = Project.findByOwnerAndProjectName("yobi", "projectYobi");

        // When
        // Then
        testProjectSearch(admin, project, acceptJson, contains(project.owner + "/" + project.name));
    }

    @Test
    public void memberCanSearchPrivateProjects() {
        // Given
        User member = User.find.byId(3L);
        Project project = Project.findByOwnerAndProjectName("laziel", "Jindo");

        // When
        // Then
        testProjectSearch(member, project, acceptHtml, contains(routes.ProjectApp.project(project.owner,project.name).url()));
    }

    @Test
    public void memberCanSearchPrivateProjectsAsJson() {
        // Given
        User member = User.find.byId(3L);
        Project project = Project.findByOwnerAndProjectName("laziel", "Jindo");

        // When
        // Then
        testProjectSearch(member, project, acceptJson, contains(project.owner + "/" + project.name));
    }

    @Test
    public void memberCanSearchPublicProjects() {
        // Given
        User member = User.find.byId(2L);
        Project project = Project.findByOwnerAndProjectName("yobi", "projectYobi");

        // When
        // Then
        testProjectSearch(member, project, acceptHtml, contains(routes.ProjectApp.project(project.owner,project.name).url()));
    }

    @Test
    public void memberCanSearchPublicProjectsAsJson() {
        // Given
        User member = User.find.byId(2L);
        Project project = Project.findByOwnerAndProjectName("yobi", "projectYobi");

        // When
        // Then
        testProjectSearch(member, project, acceptJson, contains(project.owner + "/" + project.name));
    }

    @Test
    public void anonymousCannotSearchPrivateProjects() {
        // Given
        User anonymous = User.anonymous;
        Project project = Project.findByOwnerAndProjectName("laziel", "Jindo");

        // When
        // Then
        testProjectSearch(anonymous, project, acceptHtml, doesNotContains(routes.ProjectApp.project(project.owner,project.name).url()));
    }

    @Test
    public void anonymousCannotSearchPrivateProjectsAsJson() {
        // Given
        User anonymous = User.anonymous;
        Project project = Project.findByOwnerAndProjectName("laziel", "Jindo");

        // When
        // Then
        testProjectSearch(anonymous, project, acceptJson, doesNotContains(project.owner + "/" + project.name));
    }

    @Test
    public void anonymousCanSearchPublicProjects() {
        // Given
        User anonymous = User.anonymous;
        Project project = Project.findByOwnerAndProjectName("yobi", "projectYobi");

        // When
        // Then
        testProjectSearch(anonymous, project, acceptHtml, contains(routes.ProjectApp.project(project.owner,project.name).url()));
    }

    @Test
    public void anonymousCanSearchPublicProjectsAsJson() {
        // Given
        User anonymous = User.anonymous;
        Project project = Project.findByOwnerAndProjectName("yobi", "projectYobi");

        // When
        // Then
        testProjectSearch(anonymous, project, acceptJson, contains(project.owner + "/" + project.name));
    }

    @Test
    public void delete() {
        // Given
        User member = User.find.byId(2L);
        Project project = Project.findByOwnerAndProjectName("yobi", "projectYobi");
        RecentlyVisitedProjects.addNewVisitation(member, project);

        // When
        project.delete();

        // Then
        assertThat(Project.findByOwnerAndProjectName("yobi", "projectYobi")).isNull();
    }

    private void testProjectSearch(User user, Project project, String accept, Condition<String> condition) {
        // Given
        String query = project.name;

        // When
        Result result = callAction(
                routes.ref.ProjectApp.projects(query, 1),
                fakeRequest().withHeader(Http.HeaderNames.ACCEPT, accept).withSession(
                        UserApp.SESSION_USERID, user.id.toString()));

        // Then
        assertThat(status(result)).isEqualTo(OK);
        assertThat(contentType(result)).isEqualTo(accept);
        assertThat(contentAsString(result)).is(condition);
    }

    @Test
    public void testTransferProject() {
        //Given
        Project project = Project.findByOwnerAndProjectName("yobi", "projectYobi");
        User oldOwner = User.findByLoginId("yobi");
        User newOwner = User.findByLoginId("doortts");

        Map<String,String> data = new HashMap<>();
        data.put("owner", newOwner.loginId);

        //When
        Result result = callAction(
                controllers.routes.ref.ProjectApp.transferProject(project.owner, project.name),
                fakeRequest(PUT, routes.ProjectApp.transferProject(project.owner, project.name).url()
                        + "?owner=" + newOwner.loginId)
                        .withSession(UserApp.SESSION_USERID, oldOwner.id.toString()));

        //Then
        assertThat(status(result)).isEqualTo(303); // redirection to project home
        assertThat(redirectLocation(result)).isEqualTo("/yobi/projectYobi");

        ProjectTransfer pt = ProjectTransfer.find.where()
                .eq("project", project)
                .eq("sender", oldOwner)
                .eq("destination", newOwner.loginId)
                .findUnique();

        assertThat(pt).isNotNull();
        assertThat(pt.confirmKey).isNotNull();
        assertThat(pt.accepted).isFalse();
    }

    @Test
    public void testTransferProjectToWrongUser() {
        //Given
        Project project = Project.findByOwnerAndProjectName("yobi", "projectYobi");
        User oldOwner = User.findByLoginId("yobi");
        User newOwner = User.findByLoginId("keesun");

        Map<String,String> data = new HashMap<>();
        data.put("owner", newOwner.loginId);

        //When
        Result result = callAction(
                controllers.routes.ref.ProjectApp.transferProject(project.owner, project.name),
                fakeRequest(PUT, routes.ProjectApp.transferProject(project.owner, project.name).url()
                        + "?owner=" + newOwner.loginId)
                        .withSession(UserApp.SESSION_USERID, oldOwner.id.toString()));

        //Then
        assertThat(status(result)).isEqualTo(400); // bad request
    }

    @Test
    public void testAcceptTransfer() throws Exception {
        //Given
        GitRepository.setRepoPrefix("resources/test/repo/git/");

        Project project = Project.findByOwnerAndProjectName("yobi", "projectYobi");
        RepositoryService.createRepository(project);

        User sender = User.findByLoginId("yobi");
        User newOwner = User.findByLoginId("doortts");

        ProjectTransfer pt = ProjectTransfer.requestNewTransfer(project, sender, newOwner.loginId);
        assertThat(pt.confirmKey).isNotNull();

        //When
        Result result = callAction(
                controllers.routes.ref.ProjectApp.acceptTransfer(pt.id, pt.confirmKey),
                fakeRequest(PUT, routes.ProjectApp.acceptTransfer(pt.id, pt.confirmKey).url())
                        .withSession(UserApp.SESSION_USERID, newOwner.id.toString()));

        //Then
        assertThat(status(result)).isEqualTo(303);
        assertThat(redirectLocation(result)).isEqualTo("/doortts/projectYobi");
        support.Files.rm_rf(new File(GitRepository.getRepoPrefix()));
    }

    @Test
    public void testAcceptTransferWithWrongKey() throws Exception {
        //Given
        GitRepository.setRepoPrefix("resources/test/repo/git/");

        Project project = Project.findByOwnerAndProjectName("yobi", "projectYobi");
        RepositoryService.createRepository(project);

        User sender = User.findByLoginId("yobi");
        User newOwner = User.findByLoginId("doortts");

        ProjectTransfer pt = ProjectTransfer.requestNewTransfer(project, sender, newOwner.loginId);
        assertThat(pt.confirmKey).isNotNull();

        //When
        Result result = callAction(
                controllers.routes.ref.ProjectApp.acceptTransfer(pt.id, "wrongKey"),
                fakeRequest(PUT, routes.ProjectApp.acceptTransfer(pt.id, "wrongKey").url())
                        .withSession(UserApp.SESSION_USERID, newOwner.id.toString()));

        //Then
        assertThat(status(result)).isEqualTo(400);
        support.Files.rm_rf(new File(GitRepository.getRepoPrefix()));
    }

    private Condition<String> contains(final String expected) {
        return new Condition<String>("contains '" + expected + "'") {
            @Override
            public boolean matches(String actual) {
                if (actual == null) {
                    return false;
                }
                return actual.contains(expected);
            }
        };
    }

    private Condition<String> doesNotContains(final String expected) {
        return new Condition<String>("does not contains '" + expected + "'") {
            @Override
            public boolean matches(String actual) {
                if (actual == null) {
                    return false;
                }
                return !actual.contains(expected);
            }
        };
    }
}
