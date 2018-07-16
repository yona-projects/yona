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
import utils.JodaDateUtil;

import static org.fest.assertions.Assertions.assertThat;
import static play.libs.Json.toJson;
import static play.mvc.Results.created;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({UserApi.class, Project.class, Issue.class, IssueApi.class})
public class IssueApiNewIssueCommentTest {

    protected static FakeApplication app;

    private static final String OWNER = "p.mj";
    private static final String PROJECT_NAME = "dev";
    private static final long NUMBER = 10;
    private static final String COMMENT = "new comment";

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
        PowerMockito.spy(IssueApi.class);
    }

    @Test
    public void requestBodyIsNull_returnBadRequestError() throws Exception {
        // GIVEN
        String headerValue = "token " + TOKEN;

        // WHEN
        Result result = callAction(
                routes.ref.IssueApi.newIssueComment(OWNER, PROJECT_NAME, NUMBER),
                fakeRequest().withHeader(Http.HeaderNames.AUTHORIZATION, headerValue)
        );

        // THEN
        assertThat(status(result)).isEqualTo(BAD_REQUEST);
        assertThat(contentAsString(result)).isEqualTo("Expecting Json data");
    }

    @Test
    public void tokenIsEmpty_returnUnauthorizedError() throws Exception {
        // GIVEN
        String headerValue = "token ";
        ObjectNode requestBody = Json.newObject();
        requestBody.put("comment", COMMENT);

        Project project = new Project();
        Issue issue = new Issue();

        PowerMockito.doReturn(false).when(UserApi.class, "isAuthored", Mockito.any());
        PowerMockito.doReturn(project).when(Project.class, "findByOwnerAndProjectName", Mockito.anyString(), Mockito.anyString());
        PowerMockito.doReturn(issue).when(Issue.class, "findByNumber", Mockito.any(), Mockito.anyLong());

        // WHEN
        Result result = callAction(
                routes.ref.IssueApi.newIssueComment(OWNER, PROJECT_NAME, NUMBER),
                fakeRequest().withHeader(Http.HeaderNames.AUTHORIZATION, headerValue).withJsonBody(requestBody)
        );

        // THEN
        assertThat(status(result)).isEqualTo(UNAUTHORIZED);
        assertThat(contentAsString(result)).isEqualTo("{\"message\":\"unauthorized request\"}");
    }

    @Test
    public void userByTokenAndIssueExist_returnIssueComment() throws Exception {
        // GIVEN
        String headerValue = "token " + TOKEN;
        ObjectNode requestBody = Json.newObject();
        requestBody.put("comment", COMMENT);

        Project project = new Project();
        Issue issue = new Issue();
        User user = new User();
        JsonNode expected = getResultJsonNodeByToken(COMMENT);

        PowerMockito.doReturn(true).when(UserApi.class, "isAuthored", Mockito.any());
        PowerMockito.doReturn(project).when(Project.class, "findByOwnerAndProjectName", Mockito.anyString(), Mockito.anyString());
        PowerMockito.doReturn(issue).when(Issue.class, "findByNumber", Mockito.any(), Mockito.anyLong());
        PowerMockito.doReturn(user).when(UserApi.class, "getAuthorizedUser", Mockito.any());
        PowerMockito.doReturn(created(expected)).when(IssueApi.class, "createCommentUsingToken", Mockito.any(), Mockito.any(), Mockito.anyString());

        // WHEN
        Result result = callAction(
                routes.ref.IssueApi.newIssueComment(OWNER, PROJECT_NAME, NUMBER),
                fakeRequest().withHeader(Http.HeaderNames.AUTHORIZATION, headerValue).withJsonBody(requestBody)
        );

        // THEN
        assertThat(status(result)).isEqualTo(CREATED);
        assertThat(contentAsString(result)).contains("result");
    }

    @Test
    public void userAndIssueExist_returnIssueComment() throws Exception {
        // GIVEN
        ObjectNode requestBody = Json.newObject();
        requestBody.put("comment", COMMENT);

        Project project = new Project();
        Issue issue = new Issue();
        JsonNode expected = getResultJsonNode();

        PowerMockito.doReturn(true).when(UserApi.class, "isAuthored", Mockito.any());
        PowerMockito.doReturn(project).when(Project.class, "findByOwnerAndProjectName", Mockito.anyString(), Mockito.anyString());
        PowerMockito.doReturn(issue).when(Issue.class, "findByNumber", Mockito.any(), Mockito.anyLong());
        PowerMockito.doReturn(created(expected)).when(IssueApi.class, "createCommentByUser", Mockito.any(), Mockito.any(), Mockito.any());

        // WHEN
        Result result = callAction(
                routes.ref.IssueApi.newIssueComment(OWNER, PROJECT_NAME, NUMBER),
                fakeRequest().withJsonBody(requestBody)
        );

        // THEN
        assertThat(status(result)).isEqualTo(CREATED);
        assertThat(contentAsString(result)).contains("status");
    }

    private JsonNode getResultJsonNodeByToken(String comment) {
        ObjectNode result = Json.newObject();

        ObjectNode basicNode = Json.newObject();
        basicNode.put("id", 1);
        basicNode.put("contents", comment);
        basicNode.put("createdDate", JodaDateUtil.now().toString());

        ObjectNode authorNode = Json.newObject();
        authorNode.put("id", 2);
        authorNode.put("loginId", "p.mj");
        authorNode.put("name", "박미정");
        basicNode.set("author", toJson(authorNode));

        result.set("result", toJson(basicNode));
        return result;
    }

    private JsonNode getResultJsonNode() {
        ObjectNode result = Json.newObject();

        result.put("status", 201);
        result.put("location", "path");

        return result;
    }
}
