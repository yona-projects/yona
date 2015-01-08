package controllers;

import models.Issue;
import models.Project;
import models.User;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.*;
import play.mvc.Result;
import play.test.FakeApplication;
import play.test.FakeRequest;
import play.test.Helpers;
import playRepository.GitRepository;
import playRepository.RepositoryService;
import support.ContextTest;
import utils.RouteUtil;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.*;
import static play.test.Helpers.contentAsString;

/**
 * @author Changgun Kim
 */
public class MarkdownAppTest extends ContextTest {
    protected static FakeApplication app;

    private static User testOwner;
    private static Project testProject;

    private static User anotherOwner;
    private static Project anotherProject;
    private static Issue anotherIssue;

    @BeforeClass
    public static void beforeClass() {
        GitRepository.setRepoPrefix("resources/test/repo/git/");
        Map<String, String> config = support.Helpers.makeTestConfig();
        config.put("signup.require.confirm", "true");

        app = support.Helpers.makeTestApplication(config);
        Helpers.start(app);

        callAction(
                routes.ref.Application.init()
        );

        testOwner = createUser("testOwner", "testOwner@naver.com");
        testProject = createProject(testOwner, "testProject");

        anotherOwner = createUser("anotherIssueNumber", "anotherIssue@naver.com");
        anotherProject = createProject(anotherOwner, "anotherIssueTestProject");
        anotherIssue = createIssue(anotherOwner, anotherProject, "hello", "body");

        GitRepository.setRepoPrefix("resources/test/repo/git/");
    }

    @AfterClass
    public static void afterClass() {
        support.Files.rm_rf(new File(GitRepository.getRepoPrefix()));
        Helpers.stop(app);
    }

    private static User createUser(String name, String email) {
        User user = new User();
        user.loginId = name;
        user.name = name;
        user.email = email;
        User.create(user);
        return user;
    }

    private static Project createProject(User owner, String name) {
        Project project = new Project();
        project.owner = owner.name;
        project.name = name;
        project.vcs = RepositoryService.VCS_GIT;
        Project.create(project);
        return project;
    }

    private static Issue createIssue(User user, Project project, String title, String body) {
        Issue issue = new Issue();
        issue.project = project;
        issue.setTitle(title);
        issue.setBody(body);
        issue.setAuthor(user);
        issue.save();
        return issue;
    }

    private Repository createRepository(Project proejct) throws IOException {
        GitRepository gitRepository = new GitRepository(proejct.owner, proejct.name);
        gitRepository.create();

        String wcPath = GitRepository.getRepoPrefix() + proejct.owner + "/" + "clone-" + proejct.name + ".git";
        String repoPath = wcPath + "/.git";

        Repository repository = new RepositoryBuilder().setGitDir(new File(repoPath)).build();
        repository.create(false);
        return repository;
    }

    private RevCommit createCommit(Repository repository, User user, Project proejct) throws IOException, GitAPIException {
        String testDirName = "dir";
        String testFileName = "file";
        String wcPath = GitRepository.getRepoPrefix() + proejct.owner + "/" + "clone-" + proejct.name + ".git";

        Git git = new Git(repository);
        FileUtils.forceMkdir(new File(wcPath + "/" + testDirName));
        FileUtils.touch(new File(wcPath + "/" + testFileName));
        git.add().addFilepattern(testDirName).call();
        git.add().addFilepattern(testFileName).call();
        RevCommit from = git.commit().setMessage("test").setAuthor(user.name, user.email).call();

        String branchName = "testBranch";
        git.branchCreate()
                .setName(branchName)
                .setForce(true)
                .call();

        git.push()
                .setRemote(GitRepository.getGitDirectoryURL(proejct))
                .setRefSpecs(new RefSpec(branchName + ":" + branchName))
                .setForce(true)
                .call();

        return from;
    }

    private FakeRequest makeFakeRequest(String body) {
        Map<String,String> data = new HashMap<>();
        data.put("body", body);
        return fakeRequest().withFormUrlEncodedBody(data);
    }

    public String getCommitUrl(RevCommit commit, Project project) {
        if (commit == null) return null;

        return controllers.routes.CodeHistoryApp.show(project.owner, project.name, commit.name()).url();
    }

    @Test
    public void test_ignorePattern() {
        // Given
        String notMatchBody = "nforge#12345 nforge/yobi#12345 #12345";
        Issue notMatchIssue = createIssue(testOwner, testProject, "hello", notMatchBody);

        // When
        String body = "<a href='#'>#" + notMatchIssue.getNumber() + "</a>\n" +
                "<code>\nhttp://yobi.navercorp.com #" + notMatchIssue.getNumber() + " http://yobi.navercorp.com\n</code>\n" +
                "<div id='#" + notMatchIssue.getNumber() + "'>Test</div>";
        Issue issue = createIssue(testOwner, testProject, "hello", body);

        Result result = callAction(
                controllers.routes.ref.MarkdownApp.render(testProject.owner, testProject.name),
                makeFakeRequest(issue.getBody())
        );

        // Then
        assertThat(status(result)).isEqualTo(OK);
        assertThat(contentAsString(result)).doesNotContain(RouteUtil.getUrl(notMatchIssue));
    }

    @Test
    public void test_WrappedPattern() {
        // Given
        String notMatchBody = "nforge#12345 nforge/yobi#12345 #12345";
        Issue notMatchIssue = createIssue(testOwner, testProject, "hello", notMatchBody);

        // When
        String body = "_" + testOwner.loginId + "#" + notMatchIssue.getNumber() + "-\n" +
                "A" + testOwner.loginId + "#" + notMatchIssue.getNumber() + "AA\n";
        Issue issue = createIssue(testOwner, testProject, "hello", body);

        Result result = callAction(
                controllers.routes.ref.MarkdownApp.render(testProject.owner, testProject.name),
                makeFakeRequest(issue.getBody())
        );

        // Then
        assertThat(status(result)).isEqualTo(OK);
        assertThat(contentAsString(result)).doesNotContain(RouteUtil.getUrl(notMatchIssue));
    }

    @Test
    public void test_issueNumber() {
        /**
         * Supported case
         * 1. User/#Num nforge#12345
         * 2. User/Project#Num nforge/yobi#12345
         * 3. #Num #123
         */

        // When not match
        String notMatchBody = "nforge#12345 nforge/yobi#12345 #12345";
        Issue notMatchIssue = createIssue(testOwner, testProject, "hello", notMatchBody);

        Result result = callAction(
                controllers.routes.ref.MarkdownApp.render(testProject.owner, testProject.name),
                makeFakeRequest(notMatchIssue.getBody())
        );

        // Then
        assertThat(status(result)).isEqualTo(OK);
        assertThat(contentAsString(result)).contains(notMatchBody);

        // When User/#Num nforge#12345
        String matchBody1 = testOwner.loginId + "#" + notMatchIssue.getNumber();
        Issue matchIssue1 = createIssue(testOwner, testProject, "hello", matchBody1);

        result = callAction(
                controllers.routes.ref.MarkdownApp.render(testProject.owner, testProject.name),
                makeFakeRequest(matchIssue1.getBody())
        );

        // Then
        assertThat(status(result)).isEqualTo(OK);
        assertThat(contentAsString(result)).contains(RouteUtil.getUrl(notMatchIssue));

        // When User/#Num nforge/yobi#12345
        String matchBody2 = anotherProject.toString() + "#" + anotherIssue.getNumber();
        Issue matchIssue2 = createIssue(testOwner, testProject, "hello", matchBody2);

        result = callAction(
                controllers.routes.ref.MarkdownApp.render(testProject.owner, testProject.name),
                makeFakeRequest(matchIssue2.getBody())
        );

        // Then
        assertThat(status(result)).isEqualTo(OK);
        assertThat(contentAsString(result)).contains(RouteUtil.getUrl(anotherIssue));

        // When #Num #12345
        String matchBody3 = "#" + notMatchIssue.getNumber();
        Issue matchIssue3 = createIssue(testOwner, testProject, "hello", matchBody3);

        result = callAction(
                controllers.routes.ref.MarkdownApp.render(testProject.owner, testProject.name),
                makeFakeRequest(matchIssue3.getBody())
        );

        // Then
        assertThat(status(result)).isEqualTo(OK);
        assertThat(contentAsString(result)).contains(RouteUtil.getUrl(notMatchIssue));
    }

    @Test
    public void test_SHA() throws IOException, GitAPIException {
        // Given
        RevCommit commit = createCommit(createRepository(testProject), testOwner, testProject);
        RevCommit anotherCommit = createCommit(createRepository(anotherProject), anotherOwner, anotherProject);

        /**
         * Supported case
         * 1. SHA be6a8cc1c1ecfe9489fb51e4869af15a13fc2cd2
         * 2. @SHA @be6a8cc1c1ecfe9489fb51e4869af15a13fc2cd2
         * 3. User@SHA nforge@be6a8cc1c1ecfe9489fb51e4869af15a13fc2cd2
         * 4. User/Project@SHA nforge/yobi@be6a8cc1c1ecfe9489fb51e4869af15a13fc2cd2
         */

        // When not match
        String notMatchBody = "@be6a8cc1c1ecfe9489fb51e4869af15a13fc2cd2 " +
                "@be6a8cc1c1ecfe9489fb51e4869af15a13fc2cd2 " +
                "nforge@be6a8cc1c1ecfe9489fb51e4869af15a13fc2cd2 " +
                "nforge/yobi@be6a8cc1c1ecfe9489fb51e4869af15a13fc2cd2";
        Issue notMatchIssue = createIssue(testOwner, testProject, "hello", notMatchBody);

        Result result = callAction(
                controllers.routes.ref.MarkdownApp.render(testProject.owner, testProject.name),
                makeFakeRequest(notMatchIssue.getBody())
        );

        // Then
        assertThat(status(result)).isEqualTo(OK);
        assertThat(contentAsString(result)).contains(notMatchBody);

        // When SHA be6a8cc1c1ecfe9489fb51e4869af15a13fc2cd2
        String matchBody1 = commit.getId().name();
        Issue matchIssue1 = createIssue(testOwner, testProject, "hello", matchBody1);

        result = callAction(
                controllers.routes.ref.MarkdownApp.render(testProject.owner, testProject.name),
                makeFakeRequest(matchIssue1.getBody())
        );

        // Then
        assertThat(status(result)).isEqualTo(OK);
        assertThat(contentAsString(result)).contains(getCommitUrl(commit, testProject));

        // When SHA @be6a8cc1c1ecfe9489fb51e4869af15a13fc2cd2
        String matchBody2 = "@" + commit.getId().name();
        Issue matchIssue2 = createIssue(testOwner, testProject, "hello", matchBody2);

        result = callAction(
                controllers.routes.ref.MarkdownApp.render(testProject.owner, testProject.name),
                makeFakeRequest(matchIssue2.getBody())
        );

        // Then
        assertThat(status(result)).isEqualTo(OK);
        assertThat(contentAsString(result)).contains(getCommitUrl(commit, testProject));

        // When User@SHA nforge@be6a8cc1c1ecfe9489fb51e4869af15a13fc2cd2
        String matchBody3 = testOwner.loginId + "@" + commit.getId().name();
        Issue matchIssue3 = createIssue(testOwner, testProject, "hello", matchBody3);

        result = callAction(
                controllers.routes.ref.MarkdownApp.render(testProject.owner, testProject.name),
                makeFakeRequest(matchIssue3.getBody())
        );

        // Then
        assertThat(status(result)).isEqualTo(OK);
        assertThat(contentAsString(result)).contains(getCommitUrl(commit, testProject));

        // When User/Project@SHA nforge/yobi@be6a8cc1c1ecfe9489fb51e4869af15a13fc2cd2
        String matchBody4 = anotherProject.toString() + "@" + anotherCommit.getId().name();
        Issue matchIssue4 = createIssue(testOwner, testProject, "hello", matchBody4);

        result = callAction(
                controllers.routes.ref.MarkdownApp.render(testProject.owner, testProject.name),
                makeFakeRequest(matchIssue4.getBody())
        );

        // Then
        assertThat(status(result)).isEqualTo(OK);
        assertThat(contentAsString(result)).contains(getCommitUrl(anotherCommit, anotherProject));
    }

    @Test
    public void testMention() {
        /**
         * Supported case
         * 1. @User @nforge
         * 2. @User/Project @nforge/yobi
         */

        // When not match
        String notMatchBody = "@nforge @nforge/yobi";
        Issue notMatchIssue = createIssue(testOwner, testProject, "hello", notMatchBody);

        Result result = callAction(
                controllers.routes.ref.MarkdownApp.render(testProject.owner, testProject.name),
                makeFakeRequest(notMatchIssue.getBody())
        );

        // Then
        assertThat(status(result)).isEqualTo(OK);
        assertThat(contentAsString(result)).contains(notMatchBody);

        // When @User @nforge
        String matchBody1 = "@" + testOwner.loginId;
        Issue matchIssue1 = createIssue(testOwner, testProject, "hello", matchBody1);

        result = callAction(
                controllers.routes.ref.MarkdownApp.render(testProject.owner, testProject.name),
                makeFakeRequest(matchIssue1.getBody())
        );

        // Then
        assertThat(status(result)).isEqualTo(OK);
        assertThat(contentAsString(result)).contains(RouteUtil.getUrl(testOwner));

        // When @User/nforge
        String matchBody2 = "@" + testProject.toString();
        Issue matchIssue2 = createIssue(testOwner, testProject, "hello", matchBody2);

        result = callAction(
                controllers.routes.ref.MarkdownApp.render(testProject.owner, testProject.name),
                makeFakeRequest(matchIssue2.getBody())
        );

        // Then
        assertThat(status(result)).isEqualTo(OK);
        assertThat(contentAsString(result)).contains(RouteUtil.getUrl(testProject));
    }
}

