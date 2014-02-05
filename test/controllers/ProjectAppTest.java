package controllers;

import models.Label;
import models.Project;
import models.PushedBranch;
import models.User;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ObjectNode;
import org.fest.assertions.Condition;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import play.test.FakeApplication;
import play.test.Helpers;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;
import static play.libs.Json.toJson;
import static play.test.Helpers.*;

public class ProjectAppTest {
    protected static FakeApplication app;
    private String acceptHtml = "text/html";
    private String acceptJson = "application/json";

    @BeforeClass
    public static void beforeClass() {
        callAction(
                routes.ref.Application.init()
        );
    }

    @Before
    public void before() {
        app = support.Helpers.makeTestApplication();
        Helpers.start(app);
    }

    @After
    public void after() {
        Helpers.stop(app);
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
        Iterator<Map.Entry<String, JsonNode>> fields = Json.parse(contentAsString(result)).getFields();
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
    public void memberCannotSearchPrivateProjects() {
        // Given
        User member = User.find.byId(3L);
        Project project = Project.findByOwnerAndProjectName("laziel", "Jindo");

        // When
        // Then
        testProjectSearch(member, project, acceptHtml, doesNotContains(routes.ProjectApp.project(project.owner,project.name).url()));
    }

    @Test
    public void memberCannotSearchPrivateProjectsAsJson() {
        // Given
        User member = User.find.byId(3L);
        Project project = Project.findByOwnerAndProjectName("laziel", "Jindo");

        // When
        // Then
        testProjectSearch(member, project, acceptJson, doesNotContains(project.owner + "/" + project.name));
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
