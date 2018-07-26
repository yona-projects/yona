package controllers.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import models.Issue;
import models.IssueComment;
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
import static play.mvc.Http.Status.UNAUTHORIZED;
import static play.test.Helpers.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({UserApi.class, Project.class, Issue.class, IssueApi.class})
public class IssueApiUpdateIssueCommentTest {

    protected static FakeApplication app;
    private static IssueComment mockIssueComment;
    private static Issue mockIssue;

    private static final String OWNER = "p.mj";
    private static final String PROJECT_NAME = "dev";
    private static final long NUMBER = 10;
    private static final long COMMENT_ID = 11;
    private static final String COMMENT = "update comment";

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

        mockIssueComment = PowerMockito.mock(IssueComment.class);
        mockIssue = PowerMockito.mock(Issue.class);
    }

    @Test
    public void tokenIsNull_returnUnauthorizedError() throws Exception {
        // GIVEN
        ObjectNode requestBody = Json.newObject();
        requestBody.put("comment", COMMENT);

        PowerMockito.doReturn(false).when(UserApi.class, "isAuthored", Mockito.any());

        // WHEN
        Result result = callAction(
                routes.ref.IssueApi.updateIssueComment(OWNER, PROJECT_NAME, NUMBER, COMMENT_ID),
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
        requestBody.put("comment", COMMENT);

        PowerMockito.doReturn(false).when(UserApi.class, "isAuthored", Mockito.any());

        // WHEN
        Result result = callAction(
                routes.ref.IssueApi.updateIssueComment(OWNER, PROJECT_NAME, NUMBER, COMMENT_ID),
                fakeRequest().withJsonBody(requestBody)
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
                routes.ref.IssueApi.updateIssueComment(OWNER, PROJECT_NAME, NUMBER, COMMENT_ID),
                fakeRequest().withHeader(Http.HeaderNames.AUTHORIZATION, headerValue)
        );

        // THEN
        assertThat(status(result)).isEqualTo(BAD_REQUEST);
        assertThat(contentAsString(result)).isEqualTo("{\"message\":\"Expecting Json data\"}");
    }

    @Test
    public void updatingIssueCommentIsSucceeded_returnIssueComment() throws Exception {
        // GIVEN
        String headerValue = "token " + TOKEN;
        ObjectNode requestBody = Json.newObject();
        requestBody.put("comment", COMMENT);

        User user = new User();
        Project project = new Project();
        Issue issue = new Issue();

        ObjectNode commentNode = getCommentJsonNode();
        ObjectNode authorNode = getAuthorJsonNode();

        PowerMockito.doReturn(true).when(UserApi.class, "isAuthored", Mockito.any());
        PowerMockito.doReturn(user).when(UserApi.class, "getAuthorizedUser", Mockito.anyString());
        PowerMockito.doReturn(project).when(Project.class, "findByOwnerAndProjectName", Mockito.anyString(), Mockito.anyString());
        PowerMockito.doReturn(issue).when(Issue.class, "findByNumber", Mockito.any(), Mockito.anyLong());
        PowerMockito.doReturn(mockIssueComment).when(mockIssue, "findCommentByCommentId", Mockito.anyLong());
        PowerMockito.doReturn(commentNode).when(IssueApi.class, "getCommentJsonNode", Mockito.any());
        PowerMockito.doReturn(authorNode).when(IssueApi.class, "getAuthorJsonNode", Mockito.any());

        // WHEN
        Result result = callAction(
                routes.ref.IssueApi.updateIssueComment(OWNER, PROJECT_NAME, NUMBER, COMMENT_ID),
                fakeRequest().withHeader(Http.HeaderNames.AUTHORIZATION, headerValue).withJsonBody(requestBody)
        );

        // THEN
        assertThat(status(result)).isEqualTo(CREATED);
        assertThat(contentAsString(result)).contains("result");
    }

    private ObjectNode getCommentJsonNode() {
        ObjectNode commentNode = Json.newObject();
        commentNode.put("id", 1);
        commentNode.put("contents", COMMENT);
        commentNode.put("createdDate", JodaDateUtil.now().toString());

        return commentNode;
    }

    private ObjectNode getAuthorJsonNode() {
        ObjectNode authorNode = Json.newObject();
        authorNode.put("id", 2);
        authorNode.put("loginId", "p.mj");
        authorNode.put("name", "박미정");

        return authorNode;
    }

}
