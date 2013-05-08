package controllers;

import models.*;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
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
    private User nonmember;
    private User anonymous;
    private Issue issue;

    @BeforeClass
    public static void beforeClass() {
        callAction(
                routes.ref.Application.init()
        );
    }

    @Before
    public void before() {
        app = Helpers.fakeApplication(Helpers.inMemoryDatabase());
        Helpers.start(app);

        Project project = Project.findByNameAndOwner("hobi", "nForge4java");
        admin = User.findByLoginId("admin");
        manager = User.findByLoginId("hobi");
        member = User.findByLoginId("k16wire");
        author = User.findByLoginId("nori");
        nonmember = User.findByLoginId("doortts");
        anonymous = new NullUser();

        issue = new Issue();
        issue.setProject(project);
        issue.setTitle("hello");
        issue.setBody("world");
        issue.setAuthor(author);
        issue.save();

        assertThat(this.admin.isSiteManager()).describedAs("admin is Site Admin.").isTrue();
        assertThat(ProjectUser.isManager(manager.id, project.id)).describedAs("manager is a manager").isTrue();
        assertThat(ProjectUser.isManager(member.id, project.id)).describedAs("member is not a manager").isFalse();
        assertThat(ProjectUser.isMember(member.id, project.id)).describedAs("member is a member").isTrue();
        assertThat(ProjectUser.isMember(author.id, project.id)).describedAs("author is not a member").isFalse();
        assertThat(project.isPublic).isTrue();
    }

    @After
    public void after() {
        Helpers.stop(app);
    }

    private Result postBy(User user) {
        //Given
        Map<String,String> data = new HashMap<String,String>();
        data.put("title", "hello");
        data.put("body", "world");

        //When
        return callAction(
                controllers.routes.ref.IssueApp.newIssue("hobi", "nForge4java"),
                fakeRequest()
                        .withFormUrlEncodedBody(data)
                        .withSession(UserApp.SESSION_USERID, user.getId().toString())
        );
    }

    private Result editBy(User user) {
        Map<String,String> data = new HashMap<String,String>();
        data.put("title", "bye");
        data.put("body", "universe");

        return callAction(
                controllers.routes.ref.IssueApp.editIssue("hobi", "nForge4java", issue.id),
                fakeRequest()
                        .withFormUrlEncodedBody(data)
                        .withSession(UserApp.SESSION_USERID, user.id.toString())
        );
    }

    private Result deleteBy(User user) {
        return callAction(
                controllers.routes.ref.IssueApp.deleteIssue("hobi", "nForge4java", issue.id),
                fakeRequest()
                        .withSession(UserApp.SESSION_USERID, user.id.toString())
        );
    }

    @Test
    public void editByNonmember() {
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
        // When
        Result result = postBy(anonymous);

        // Then
        assertThat(status(result)).describedAs("Anonymous can't post an issue.").isEqualTo(FORBIDDEN);
    }

    @Test
    public void postByNonmember() {
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
}
