package controllers.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.Issue;
import models.Project;
import models.User;
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

import static org.fest.assertions.Assertions.assertThat;
import static play.libs.Json.toJson;
import static play.mvc.Http.Status.UNAUTHORIZED;
import static play.mvc.Results.created;
import static play.test.Helpers.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({IssueApi.class, UserApi.class, Project.class, Issue.class})
public class IssueApiUpdateIssueTest {

    protected static FakeApplication app;

    private static final String OWNER = "p.mj";
    private static final String PROJECT_NAME = "dev";
    private static final long NUMBER = 10;
    private static final String TITLE = "update title";

    private static final String TOKEN = "abc9F2AAL+3d1FbSyJqO2bxX6QcFLBLNWTfOI07N00k=";

    @BeforeClass
    public static void beforeClass() {
        app = support.Helpers.makeTestApplication();
        Helpers.start(app);
    }

    @Before
    public void before() throws Exception {
        PowerMockito.spy(UserApi.class);
        PowerMockito.spy(IssueApi.class);
        PowerMockito.spy(Project.class);
        PowerMockito.spy(Issue.class);
    }

    @Test
    public void tokenIsNull_returnUnauthorizedError() throws Exception {
        // GIVEN
        ObjectNode requestBody = Json.newObject();
        requestBody.put("title", TITLE);

        PowerMockito.doReturn(false).when(UserApi.class, "isAuthored", Mockito.any());

        // WHEN
        Result result = callAction(
                routes.ref.IssueApi.updateIssue(OWNER, PROJECT_NAME, NUMBER),
                fakeRequest().withJsonBody(requestBody)
        );

        // THEN
        assertThat(status(result)).isEqualTo(UNAUTHORIZED);
        assertThat(contentAsString(result)).isEqualTo("{\"message\":\"unauthorized request\"}");
    }

    @Test
    public void tokenIsEmpty_returnUnauthorizedError() throws Exception {
        // GIVEN
        String headerValue = "token ";
        ObjectNode requestBody = Json.newObject();
        requestBody.put("title", TITLE);

        PowerMockito.doReturn(false).when(UserApi.class, "isAuthored", Mockito.any());

        // WHEN
        Result result = callAction(
                routes.ref.IssueApi.updateIssue(OWNER, PROJECT_NAME, NUMBER),
                fakeRequest().withHeader(Http.HeaderNames.AUTHORIZATION, headerValue).withJsonBody(requestBody)
        );

        // THEN
        assertThat(status(result)).isEqualTo(UNAUTHORIZED);
        assertThat(contentAsString(result)).isEqualTo("{\"message\":\"unauthorized request\"}");
    }

    @Test
    public void requestBodyIsNull_returnBadRequestError() throws Exception {
        // GIVEN
        String headerValue = "token " + TOKEN;

        PowerMockito.doReturn(true).when(UserApi.class, "isAuthored", Mockito.any());

        // WHEN
        Result result = callAction(
                routes.ref.IssueApi.updateIssue(OWNER, PROJECT_NAME, NUMBER),
                fakeRequest().withHeader(Http.HeaderNames.AUTHORIZATION, headerValue)
        );

        // THEN
        assertThat(status(result)).isEqualTo(BAD_REQUEST);
        assertThat(contentAsString(result)).isEqualTo("{\"message\":\"Expecting Json data\"}");
    }

    @Test
    public void updatingIssueISSucceeded_returnIssue() throws Exception {
        // GIVEN
        String headerValue = "token " + TOKEN;
        ObjectNode requestBody = Json.newObject();
        requestBody.put("title", TITLE);

        User user = new User();
        Project project = new Project();
        Issue issue = new Issue();

        JsonNode expected = getResultJsonNode();

        PowerMockito.doReturn(true).when(UserApi.class, "isAuthored", Mockito.any());
        PowerMockito.doReturn(user).when(UserApi.class, "getAuthorizedUser", Mockito.anyString());
        PowerMockito.doReturn(project).when(Project.class, "findByOwnerAndProjectName", Mockito.anyString(), Mockito.anyString());
        PowerMockito.doReturn(issue).when(Issue.class, "findByNumber", Mockito.any(), Mockito.anyLong());
        PowerMockito.doReturn(created(expected)).when(IssueApi.class, "updateIssueNode", Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        // WHEN
        Result result = callAction(
                routes.ref.IssueApi.updateIssue(OWNER, PROJECT_NAME, NUMBER),
                fakeRequest().withHeader(Http.HeaderNames.AUTHORIZATION, headerValue).withJsonBody(requestBody)
        );

        // THEN
        assertThat(status(result)).isEqualTo(CREATED);
        assertThat(contentAsString(result)).contains("result");
    }

    private JsonNode getResultJsonNode() {
        ObjectNode result = Json.newObject();
        ObjectNode issueNode = Json.newObject();

        issueNode.put("number", 1);
        issueNode.put("id",2);
        issueNode.put("title", TITLE);


        return result.set("result", toJson(issueNode));
    }
}
