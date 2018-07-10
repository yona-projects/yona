package controllers.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.Issue;
import models.Project;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import play.test.FakeApplication;
import play.test.Helpers;
import utils.JodaDateUtil;

import static org.fest.assertions.Assertions.assertThat;
import static play.libs.Json.toJson;
import static play.mvc.Results.ok;
import static play.test.Helpers.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({UserApi.class, Project.class, Issue.class, ProjectApi.class, IssueApi.class})
public class IssueApiGetIssueTest {

    protected static FakeApplication app;

    private static final String OWNER = "p.mj";
    private static final String PROJECT_NAME = "dev";
    private static final long NUMBER = 10;

    private static final String TOKEN = "abc9F2AAL+3d1FbSyJqO2bxX6QcFLBLNWTfOI07N00k=";

    @BeforeClass
    public static void beforeClass() {
        app = support.Helpers.makeTestApplication();
        Helpers.start(app);
    }

    @Before
    public void before() throws Exception {
        PowerMockito.spy(UserApi.class);
        PowerMockito.spy(Project.class);
        PowerMockito.spy(Issue.class);
        PowerMockito.spy(ProjectApi.class);
        PowerMockito.spy(IssueApi.class);
    }

    @Test
    public void headerIsNull_returnUnauthorizedError() throws Exception {
        // GIVEN
        PowerMockito.doReturn(false).when(UserApi.class, "isAuthored", Mockito.any());

        // WHEN
        Result result = callAction(
                routes.ref.IssueApi.getIssue(OWNER, PROJECT_NAME, NUMBER)
        );

        // THEN
        assertThat(status(result)).isEqualTo(UNAUTHORIZED);
        assertThat(contentAsString(result)).isEqualTo("{\"message\":\"unauthorized request\"}");
    }

    @Test
    public void tokenIsEmpty_returnUnauthorizedError() throws Exception {
        // GIVEN
        String headerValue = "token ";

        PowerMockito.doReturn(false).when(UserApi.class, "isAuthored", Mockito.any());

        // WHEN
        Result result = callAction(
                routes.ref.IssueApi.getIssue(OWNER, PROJECT_NAME, NUMBER),
                fakeRequest().withHeader(Http.HeaderNames.AUTHORIZATION, headerValue)
        );

        // THEN
        assertThat(status(result)).isEqualTo(UNAUTHORIZED);
        assertThat(contentAsString(result)).isEqualTo("{\"message\":\"unauthorized request\"}");
    }

    @Test
    public void projectDoesNotExist_returnBadRequestError() throws Exception {
        // GIVEN
        String headerValue = "token " + TOKEN;

        PowerMockito.doReturn(true).when(UserApi.class, "isAuthored", Mockito.any());
        PowerMockito.doReturn(null).when(Project.class, "findByOwnerAndProjectName", Mockito.anyString(), Mockito.anyString());

        // WHEN
        Result result = callAction(
                routes.ref.IssueApi.getIssue(OWNER, PROJECT_NAME, NUMBER),
                fakeRequest().withHeader(Http.HeaderNames.AUTHORIZATION, headerValue)
        );

        // THEN
        assertThat(status(result)).isEqualTo(BAD_REQUEST);
        assertThat(contentAsString(result)).isEqualTo("{\"message\":\"no project by request\"}");
    }

    @Test
    public void issueDoesNotExist_returnBadRequestError() throws Exception {
        // GIVEN
        String headerValue = "token " + TOKEN;
        Project project = new Project();

        PowerMockito.doReturn(true).when(UserApi.class, "isAuthored", Mockito.any());
        PowerMockito.doReturn(project).when(Project.class, "findByOwnerAndProjectName", Mockito.anyString(), Mockito.anyString());
        PowerMockito.doReturn(null).when(Issue.class, "findByNumber", Mockito.any(), Mockito.anyLong());

        // WHEN
        Result result = callAction(
                routes.ref.IssueApi.getIssue(OWNER, PROJECT_NAME, NUMBER),
                fakeRequest().withHeader(Http.HeaderNames.AUTHORIZATION, headerValue)
        );

        // THEN
        assertThat(status(result)).isEqualTo(BAD_REQUEST);
        assertThat(contentAsString(result)).isEqualTo("{\"message\":\"no issue by request\"}");
    }

    @Test
    public void issueExists_returnIssueDetail() throws Exception {
        // GIVEN
        String headerValue = "token " + TOKEN;
        Project project = new Project();
        Issue issue = new Issue();
        ObjectNode json = getIssueJsonNode();
        JsonNode expected = getResultJsonNode(json);

        PowerMockito.doReturn(true).when(UserApi.class, "isAuthored", Mockito.any());
        PowerMockito.doReturn(project).when(Project.class, "findByOwnerAndProjectName", Mockito.anyString(), Mockito.anyString());
        PowerMockito.doReturn(issue).when(Issue.class, "findByNumber", Mockito.any(), Mockito.anyLong());
        PowerMockito.doReturn(json).when(ProjectApi.class, "getResult", Mockito.any());
        PowerMockito.doReturn(ok(expected)).when(IssueApi.class, "addIssueEvents", Mockito.any(), Mockito.any());

        // WHEN
        Result result = callAction(
                routes.ref.IssueApi.getIssue(OWNER, PROJECT_NAME, NUMBER),
                fakeRequest().withHeader(Http.HeaderNames.AUTHORIZATION, headerValue)
        );

        // THEN
        assertThat(status(result)).isEqualTo(OK);
        assertThat(contentAsString(result)).contains("result");
    }

    private ObjectNode getIssueJsonNode() {
        ObjectNode json = Json.newObject();

        json.put("number", 1);
        json.put("id", 2);
        json.put("title", "issue title");
        json.put("type", "issue type");
        json.put("author", "issue author");
        json.put("createdAt", JodaDateUtil.now().toString());
        json.put("updatedAt", JodaDateUtil.now().toString());
        json.put("body", "issue body");

        return json;
    }

    private JsonNode getResultJsonNode(ObjectNode json) {
        return Json.newObject().set("result", toJson(json));
    }
}
