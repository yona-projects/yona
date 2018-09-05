package controllers.api;

import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.PagingList;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.Issue;
import models.User;
import models.enumeration.State;
import models.support.IssueSearchCondition;
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
import static play.mvc.Results.ok;
import static play.test.Helpers.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({UserApi.class, IssueSearchCondition.class})
public class UserApiGetIssuesByUserTest {

    protected static FakeApplication app;

    private static final String FILTER = "assigned";
    private static final int PAGE = 1;
    private static final int PAGE_NUM = 10;

    private static final String TOKEN = "abc9F2AAL+3d1FbSyJqO2bxX6QcFLBLNWTfOI07N00k=";

    private static User USER;

    @BeforeClass
    public static void beforeClass() {
        app = support.Helpers.makeTestApplication();
        Helpers.start(app);

        USER = new User();
        USER.id = 1L;
    }

    @Before
    public void before() throws Exception {
        PowerMockito.spy(UserApi.class);
        PowerMockito.doReturn(true).when(UserApi.class, "isAuthored", Mockito.any());
        PowerMockito.doReturn(USER).when(UserApi.class, "getAuthorizedUser", Mockito.anyString());

        ExpressionList<Issue> el = Issue.finder.where();
        IssueSearchCondition mockIssueSearchCondition = Mockito.mock(IssueSearchCondition.class);
        Mockito.when(mockIssueSearchCondition.getExpressionListByFilter(Mockito.any(), Mockito.any())).thenReturn(el);

        ExpressionList<Issue> mockExpressionList = PowerMockito.mock(ExpressionList.class);
        PowerMockito.doReturn(null).when(mockExpressionList, "findPagingList", Mockito.anyInt());

        PagingList<Issue> mockPagingList = PowerMockito.mock(PagingList.class);
        PowerMockito.doReturn(null).when(mockPagingList, "getPage", Mockito.anyInt());
    }

    @Test
    public void headerIsNull_returnUnauthorizedError() throws Exception {
        // GIVEN
        PowerMockito.spy(UserApi.class);
        PowerMockito.doReturn(false).when(UserApi.class, "isAuthored", Mockito.any());

        // WHEN
        Result result = callAction(
                routes.ref.UserApi.getIssuesByUser(FILTER, PAGE, PAGE_NUM)
        );

        // THEN
        assertThat(status(result)).isEqualTo(UNAUTHORIZED);
        assertThat(contentAsString(result)).isEqualTo("{\"message\":\"unauthorized request\"}");
    }

    @Test
    public void tokenIsEmpty_returnUnauthorizedError() throws Exception {
        // GIVEN
        String headerValue = "token ";

        PowerMockito.spy(UserApi.class);
        PowerMockito.doReturn(false).when(UserApi.class, "isAuthored", Mockito.any());

        // WHEN
        Result result = callAction(
                routes.ref.UserApi.getIssuesByUser(FILTER, PAGE, PAGE_NUM),
                fakeRequest().withHeader(Http.HeaderNames.AUTHORIZATION, headerValue)
        );

        // THEN
        assertThat(status(result)).isEqualTo(UNAUTHORIZED);
        assertThat(contentAsString(result)).isEqualTo("{\"message\":\"unauthorized request\"}");
    }

    @Test
    public void tokenIsValid_returnIssueList() throws Exception {
        // GIVEN
        String headerValue = "token " + TOKEN;

        ObjectNode expected = getResultJsonNode();
        PowerMockito.doReturn(ok(expected)).when(UserApi.class, "issuesAsJson", Mockito.any());

        // WHEN
        Result result = callAction(
                routes.ref.UserApi.getIssuesByUser(FILTER, PAGE, PAGE_NUM),
                fakeRequest().withHeader(Http.HeaderNames.AUTHORIZATION, headerValue)
        );

        // THEN
        assertThat(status(result)).isEqualTo(OK);
        assertThat(contentAsString(result)).contains("result");
    }

    private ObjectNode getResultJsonNode() {
        ObjectNode result = Json.newObject();
        ArrayNode issueArray = Json.newObject().arrayNode();

        ObjectNode issueNode = Json.newObject();
        issueNode.put("id", 1);
        issueNode.put("number", 2);
        issueNode.put("state", State.OPEN.toString());
        issueNode.put("title", "issue title");
        issueNode.put("createdDate", JodaDateUtil.now().toString());
        issueNode.put("updatedDate", JodaDateUtil.now().toString());

        issueArray.add(issueNode);
        result.put("result", issueArray);
        return result;
    }
}
