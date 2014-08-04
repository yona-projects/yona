package models;

import models.enumeration.ProjectScope;
import models.enumeration.RoleType;
import org.junit.*;
import play.test.FakeApplication;
import play.test.Helpers;

import java.util.HashSet;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

public class SearchTests {

    private static Organization labs;
    private static Project publicProject; // labs's project
    private static Project protectedProject; // labs's project
    private static Project privateProject; // labs's project

    private static User author; // author of a privateProject's post and issue
    private static User projectMember; // member of a privateProject
    private static User groupMember; // member of labs
    private static User groupAndProjectMember; // member of a privateProject and member of labs
    private static User assignee; // assignee of all issues

    private static Posting publicPost;
    private static Posting protectedPost;
    private static Posting privatePost;

    private static Issue privateIssue;
    private static Issue publicIssue;
    private static Issue protectedIssue;

    private static Milestone publicMilestone;
    private static Milestone protectedMilestone;
    private static Milestone privateMilestone;

    private static IssueComment publicIssueComment;
    private static IssueComment protectedIssueComment;
    private static IssueComment privateIssueComment;

    private static PostingComment publicPostComment;
    private static PostingComment protectedPostComment;
    private static PostingComment privatePostComment;

    private static ReviewComment publicReviewComment;
    private static ReviewComment protectedReviewComment;
    private static ReviewComment privateReviewComment;

    private PageParam onePageFiveSize = new PageParam(0, 5);

    protected static FakeApplication app;

    @BeforeClass
    public static void startApp() {
        app = support.Helpers.makeTestApplication();
        Helpers.start(app);

        // Given
        author = User.find.byId(6l);
        groupAndProjectMember = User.find.byId(2l);
        projectMember = User.find.byId(3l);
        groupMember = User.find.byId(4l);
        assignee = User.find.byId(5l);

        labs = Organization.find.byId(1l);
        OrganizationUser.assignRole(groupMember.id, labs.id, RoleType.ORG_MEMBER.roleType());

        publicProject = new Project();
        publicProject.projectScope = ProjectScope.PUBLIC;
        publicProject.name = "public project";
        publicProject.organization = labs;
        publicProject.save();

        protectedProject = new Project();
        protectedProject.projectScope = ProjectScope.PROTECTED;
        protectedProject.name = "protected project";
        protectedProject.organization = labs;
        protectedProject.save();

        privateProject = new Project();
        privateProject.projectScope = ProjectScope.PRIVATE;
        privateProject.name = "private project";
        privateProject.organization = labs;
        privateProject.save();

        ProjectUser.assignRole(projectMember.id, privateProject.id, RoleType.MEMBER);
        ProjectUser.assignRole(groupAndProjectMember.id, privateProject.id, RoleType.MEMBER);

        assertThat(OrganizationUser.exist(labs.id, groupAndProjectMember.id));

        publicPost = new Posting();
        publicPost.project = publicProject;
        publicPost.title = "public post";
        publicPost.save();

        protectedPost = new Posting();
        protectedPost.project = protectedProject;
        protectedPost.title = "protected post";
        protectedPost.save();

        privatePost = new Posting();
        privatePost.project = privateProject;
        privatePost.title = "private post";
        privatePost.setAuthor(author);
        privatePost.save();

        publicIssue = new Issue();
        publicIssue.project = publicProject;
        publicIssue.title = "public issue";

        publicIssue.save();

        protectedIssue = new Issue();
        protectedIssue.project = protectedProject;
        protectedIssue.title = "protected issue";
        protectedIssue.save();

        privateIssue = new Issue();
        privateIssue.project = privateProject;
        privateIssue.title = "private issue";
        privateIssue.setAuthor(author);
        privateIssue.assignee = Assignee.add(assignee.id, privateProject.id);
        privateIssue.save();

        publicMilestone = new Milestone();
        publicMilestone.project = publicProject;
        publicMilestone.title = "public milestone";
        publicMilestone.save();

        protectedMilestone = new Milestone();
        protectedMilestone.project = protectedProject;
        protectedMilestone.title = "protected milestone";
        protectedMilestone.save();

        privateMilestone = new Milestone();
        privateMilestone.project = privateProject;
        privateMilestone.title = "private milestone";
        privateMilestone.save();

        publicIssueComment = new IssueComment();
        publicIssueComment.contents = "public comment";
        publicIssueComment.issue = publicIssue;
        publicIssueComment.save();

        protectedIssueComment = new IssueComment();
        protectedIssueComment.contents = "protected comment";
        protectedIssueComment.issue = protectedIssue;
        protectedIssueComment.save();

        privateIssueComment = new IssueComment();
        privateIssueComment.contents = "private comment";
        privateIssueComment.issue = privateIssue;
        privateIssueComment.setAuthor(author);
        privateIssueComment.save();

        publicPostComment = new PostingComment();
        publicPostComment.contents = "public comment";
        publicPostComment.posting = publicPost;
        publicPostComment.save();

        protectedPostComment = new PostingComment();
        protectedPostComment.contents = "protected comment";
        protectedPostComment.posting = protectedPost;
        protectedPostComment.save();

        privatePostComment = new PostingComment();
        privatePostComment.contents = "private comment";
        privatePostComment.posting = privatePost;
        privatePostComment.setAuthor(author);
        privatePostComment.save();

        CommentThread publicCommentThread = new NonRangedCodeCommentThread();
        publicCommentThread.project = publicProject;
        publicCommentThread.save();
        publicReviewComment = new ReviewComment();
        publicReviewComment.setContents("public review");
        publicReviewComment.thread = publicCommentThread;
        publicReviewComment.save();

        CommentThread protectedCommentThread = new NonRangedCodeCommentThread();
        protectedCommentThread.project = protectedProject;
        protectedCommentThread.save();
        protectedReviewComment = new ReviewComment();
        protectedReviewComment.setContents("protected review");
        protectedReviewComment.thread = protectedCommentThread;
        protectedReviewComment.save();

        CommentThread privateCommentThread = new NonRangedCodeCommentThread();
        privateCommentThread.project = privateProject;
        privateCommentThread.save();
        privateReviewComment = new ReviewComment();
        privateReviewComment.setContents("private review");
        privateReviewComment.thread = privateCommentThread;
        privateReviewComment.author = new UserIdent(author);
        privateReviewComment.save();
    }

    @AfterClass
    public static void stopApp() {
        Helpers.stop(app);
    }

    /**
     * Find Users
     */

    @Test
    public void findUsersByLoginId() {
        // When
        List<User> users = Search.findUsers("door", onePageFiveSize).getList();
        // Then
        assertThat(users.size()).isEqualTo(1);
        assertThat(users).onProperty("name").contains("suwon");
    }

    @Test
    public void findUsersByName() {
        // When
        List<User> users = Search.findUsers("suwon", onePageFiveSize).getList();
        // Then
        assertThat(users.size()).isEqualTo(1);
        assertThat(users).onProperty("loginId").contains("doortts");
    }

    @Test
    public void findUser_from_public_project() {
        // When
        List<User> users = Search.findUsers("Jihan", Project.find.byId(2l), onePageFiveSize).getList();
        // Then
        assertThat(users.size()).isEqualTo(1);
        assertThat(users).onProperty("loginId").contains("laziel");
    }

    @Test
    public void findUser_from_organization() {
        // When
        List<User> users = Search.findUsers(groupMember.name, labs, onePageFiveSize).getList();

        // Then
        assertThat(users.size()).isEqualTo(1);
        assertThat(users).onProperty("loginId").contains(groupMember.loginId);
    }

    /**
     * Find Projects
     */

    @Test
    public void anonymous_findProjects() {
        // When
        List<Project> projects = Search.findProjects("yobi", User.anonymous, onePageFiveSize).getList();
        // Then
        assertThat(projects.size()).isEqualTo(2);
        assertThat(projects).onProperty("name").contains("projectYobi");
        assertThat(projects).onProperty("name").contains("projectYobi-1");
    }

    @Test
    public void groupMember_findProjects() {
        // When
        List<Project> projects = Search.findProjects("project", groupMember, onePageFiveSize).getList();
        // Then
        assertThat(projects.size()).isEqualTo(4);
        assertThat(projects).onProperty("name").contains("projectYobi");
        assertThat(projects).onProperty("name").contains("projectYobi-1");
        assertThat(projects).onProperty("name").contains("public project");
        assertThat(projects).onProperty("name").contains("protected project");
    }

    @Test
    public void projectMember_findProjects() {
        // When
        List<Project> projects = Search.findProjects("project", projectMember, onePageFiveSize).getList();
        // Then
        assertThat(projects.size()).isEqualTo(4);
        assertThat(projects).onProperty("name").contains("projectYobi");
        assertThat(projects).onProperty("name").contains("projectYobi-1");
        assertThat(projects).onProperty("name").contains("public project");
        assertThat(projects).onProperty("name").contains("private project");
    }

    @Test
    public void projectAndGroupMember_findProjects() {
        // When
        List<Project> projects = Search.findProjects("project", groupAndProjectMember, onePageFiveSize).getList();
        // Then
        assertThat(projects.size()).isEqualTo(5);
        assertThat(projects).onProperty("name").contains("projectYobi");
        assertThat(projects).onProperty("name").contains("projectYobi-1");
        assertThat(projects).onProperty("name").contains("public project");
        assertThat(projects).onProperty("name").contains("protected project");
        assertThat(projects).onProperty("name").contains("private project");
    }

    /**
     * Find Posts
     */

    @Test
    public void anonymous_findPosts_from_all_repos() {
        // When
        List<Posting> posts = Search.findPosts("post", User.anonymous, onePageFiveSize).getList();
        // Then
        assertThat(posts.size()).isEqualTo(1);
        assertThat(posts).onProperty("title").contains("public post");
    }

    @Test
    public void autor_findPosts_from_all_repos() {
        // When
        List<Posting> posts = Search.findPosts("post", author, onePageFiveSize).getList();
        // Then
        assertThat(posts.size()).isEqualTo(2);
        assertThat(posts).onProperty("title").contains("public post");
        assertThat(posts).onProperty("title").contains("private post");

    }

    @Test
    public void groupMember_findPosts_from_all_repos() {
        // When
        List<Posting> posts = Search.findPosts("post", groupMember, onePageFiveSize).getList();
        // Then
        assertThat(posts.size()).isEqualTo(2);
        assertThat(posts).onProperty("title").contains("public post");
        assertThat(posts).onProperty("title").contains("protected post");
    }

    @Test
    public void projectMember_findPosts_from_all_repos() {
        // When
        List<Posting> posts = Search.findPosts("post", projectMember, onePageFiveSize).getList();
        // Then
        assertThat(posts.size()).isEqualTo(2);
        assertThat(posts).onProperty("title").contains("public post");
        assertThat(posts).onProperty("title").contains("private post");

    }

    @Test
    public void groupMemberAndProjectMember_findPosts_from_all_repos() {
        // When
        List<Posting> posts = Search.findPosts("post", groupAndProjectMember, onePageFiveSize).getList();
        // Then
        assertThat(posts.size()).isEqualTo(3);
        assertThat(posts).onProperty("title").contains("public post");
        assertThat(posts).onProperty("title").contains("protected post");
        assertThat(posts).onProperty("title").contains("private post");
    }

    @Test
    public void anonymous_findPosts_from_public_project() {
        // When
        List<Posting> posts = Search.findPosts("post", User.anonymous, publicProject, onePageFiveSize).getList();
        // Then
        assertThat(posts.size()).isEqualTo(1);
        assertThat(posts).onProperty("title").contains("public post");
    }

    @Test
    public void anonymous_findPosts_from_protected_project() {
        // When
        List<Posting> posts = Search.findPosts("post", User.anonymous, protectedProject, onePageFiveSize).getList();
        // Then
        assertThat(posts.size()).isEqualTo(0);

    }

    @Test
    public void anonymous_findPosts_from_private_project() {
        // When
        List<Posting> posts = Search.findPosts("post", User.anonymous, privateProject, onePageFiveSize).getList();
        // Then
        assertThat(posts.size()).isEqualTo(0);

    }

    @Test
    public void groupMember_findPosts_from_public_project() {
        // When
        List<Posting> posts = Search.findPosts("post", groupMember, publicProject, onePageFiveSize).getList();
        // Then
        assertThat(posts.size()).isEqualTo(1);
        assertThat(posts).onProperty("title").contains("public post");

    }

    @Test
    public void groupMember_findPosts_from_protected_project() {
        // When
        List<Posting> posts = Search.findPosts("post", groupMember, protectedProject, onePageFiveSize).getList();
        // Then
        assertThat(posts.size()).isEqualTo(1);
        assertThat(posts).onProperty("title").contains("protected post");
    }

    @Test
    public void groupMember_findPosts_from_private_project() {
        // When
        List<Posting> posts = Search.findPosts("post", groupMember, privateProject, onePageFiveSize).getList();
        // Then
        assertThat(posts.size()).isEqualTo(0);
    }

    @Test
    public void projectMember_findPosts_from_public_project() {
        // When
        List<Posting> posts = Search.findPosts("post", projectMember, publicProject, onePageFiveSize).getList();
        // Then
        assertThat(posts.size()).isEqualTo(1);
        assertThat(posts).onProperty("title").contains("public post");
    }

    @Test
    public void projectMember_findPosts_from_protected_project() {
        // When
        List<Posting> posts = Search.findPosts("post", projectMember, protectedProject, onePageFiveSize).getList();
        // Then
        assertThat(posts.size()).isEqualTo(0);
    }

    @Test
    public void projectMember_findPosts_from_private_project() {
        // When & Then
        List<Posting> posts = Search.findPosts("post", projectMember, privateProject, onePageFiveSize).getList();
        assertThat(posts.size()).isEqualTo(1);
        assertThat(posts).onProperty("title").contains("private post");
    }

    @Test
    public void groupAndProjectMember_findPosts_from_protected_project() {
        // When
        List<Posting> posts = Search.findPosts("post", groupAndProjectMember, protectedProject, onePageFiveSize).getList();
        // Then
        assertThat(posts.size()).isEqualTo(1);
        assertThat(posts).onProperty("title").contains("protected post");
    }

    @Test
    public void groupAndProjectMember_findPosts_from_private_project() {
        // When
        List<Posting> posts = Search.findPosts("post", groupAndProjectMember, privateProject, onePageFiveSize).getList();
        // Then
        assertThat(posts.size()).isEqualTo(1);
        assertThat(posts).onProperty("title").contains("private post");
    }

    @Test
    public void author_findPosts_from_public_project() {
        // When
        List<Posting> posts = Search.findPosts("post", author, publicProject, onePageFiveSize).getList();
        // Then
        assertThat(posts.size()).isEqualTo(1);
        assertThat(posts).onProperty("title").contains("public post");
    }

    @Test
    public void author_findPosts_from_protected_project() {
        // When
        List<Posting> posts = Search.findPosts("post", author, protectedProject, onePageFiveSize).getList();
        // Then
        assertThat(posts.size()).isEqualTo(0);
    }

    @Test
    public void author_findPosts_from_private_project() {
        // When
        List<Posting> posts = Search.findPosts("post", author, privateProject, onePageFiveSize).getList();
        // Then
        assertThat(posts.size()).isEqualTo(1);
        assertThat(posts).onProperty("title").contains("private post");
    }

    @Test
    public void anonymous_findPosts_from_group() {
        // When
        List<Posting> posts = Search.findPosts("post", User.anonymous, labs, onePageFiveSize).getList();
        // Then
        assertThat(posts.size()).isEqualTo(1);
        assertThat(posts).onProperty("title").contains("public post");
    }

    @Test
    public void groupMember_findPosts_from_group() {
        // When
        List<Posting> posts = Search.findPosts("post", groupMember, labs, onePageFiveSize).getList();
        // Then
        assertThat(posts.size()).isEqualTo(2);
        assertThat(posts).onProperty("title").contains("public post");
        assertThat(posts).onProperty("title").contains("protected post");

    }

    @Test
    public void projectMember_findPosts_from_group() {
        // When
        List<Posting> posts = Search.findPosts("post", projectMember, labs, onePageFiveSize).getList();
        // Then
        assertThat(posts.size()).isEqualTo(2);
        assertThat(posts).onProperty("title").contains("public post");
        assertThat(posts).onProperty("title").contains("private post");

    }

    @Test
    public void projectAndGroupMember_findPosts_from_group() {
        // When
        List<Posting> posts = Search.findPosts("post", groupAndProjectMember, labs, onePageFiveSize).getList();
        // Then
        assertThat(posts.size()).isEqualTo(3);
        assertThat(posts).onProperty("title").contains("public post");
        assertThat(posts).onProperty("title").contains("protected post");
        assertThat(posts).onProperty("title").contains("private post");
    }

    @Test
    public void author_findPosts_from_group() {
        // When
        List<Posting> posts = Search.findPosts("post", author, labs, onePageFiveSize).getList();
        // Then
        assertThat(posts.size()).isEqualTo(2);
        assertThat(posts).onProperty("title").contains("public post");
        assertThat(posts).onProperty("title").contains("private post");
    }

    /**
     * Find Issues
     */

    @Test
    public void anonymous_findIssues_from_all_repos() {
        // When
        List<Issue> issues = Search.findIssues("issue", User.anonymous, onePageFiveSize).getList();
        // Then
        assertThat(issues.size()).isEqualTo(1);
        assertThat(issues).onProperty("title").contains("public issue");
    }

    @Test
    public void projectMember_findIssues_from_all_repos() {
        // When
        List<Issue> issues = Search.findIssues("issue", projectMember, onePageFiveSize).getList();
        // Then
        assertThat(issues.size()).isEqualTo(2);
        assertThat(issues).onProperty("title").contains("public issue");
        assertThat(issues).onProperty("title").contains("private issue");
    }

    @Test
    public void groupMember_findIssues_from_all_repos() {
        // When
        List<Issue> issues = Search.findIssues("issue", groupMember, onePageFiveSize).getList();
        // Then
        assertThat(issues.size()).isEqualTo(2);
        assertThat(issues).onProperty("title").contains("public issue");
        assertThat(issues).onProperty("title").contains("protected issue");
    }

    @Test
    public void assignee_findIssues_from_all_repos() {
        // When
        List<Issue> issues = Search.findIssues("issue", assignee, onePageFiveSize).getList();
        // Then
        assertThat(issues.size()).isEqualTo(2);
        assertThat(issues).onProperty("title").contains("public issue");
        assertThat(issues).onProperty("title").contains("private issue");
    }

    @Test
    public void anonymous_findIssues_from_group() {
        // When
        List<Issue> issues = Search.findIssues("issue", User.anonymous, labs, onePageFiveSize).getList();
        // Then
        assertThat(issues.size()).isEqualTo(1);
        assertThat(issues).onProperty("title").contains("public issue");
    }

    @Test
    public void projectMember_findIssues_from_group() {
        // When
        List<Issue> issues = Search.findIssues("issue", projectMember, labs, onePageFiveSize).getList();
        // Then
        assertThat(issues.size()).isEqualTo(2);
        assertThat(issues).onProperty("title").contains("public issue");
        assertThat(issues).onProperty("title").contains("private issue");
    }

    @Test
    public void groupMember_findIssues_from_group() {
        // When
        List<Issue> issues = Search.findIssues("issue", groupMember, labs, onePageFiveSize).getList();
        // Then
        assertThat(issues.size()).isEqualTo(2);
        assertThat(issues).onProperty("title").contains("public issue");
        assertThat(issues).onProperty("title").contains("protected issue");
    }

    @Test
    public void groupAndProjectMember_findIssues_from_group() {
        // When
        List<Issue> issues = Search.findIssues("issue", groupAndProjectMember, labs, onePageFiveSize).getList();
        // Then
        assertThat(issues.size()).isEqualTo(3);
        assertThat(issues).onProperty("title").contains("public issue");
        assertThat(issues).onProperty("title").contains("protected issue");
        assertThat(issues).onProperty("title").contains("private issue");
    }

    @Test
    public void author_findIssues_from_group() {
        // When
        List<Issue> issues = Search.findIssues("issue", author, labs, onePageFiveSize).getList();
        // Then
        assertThat(issues.size()).isEqualTo(2);
        assertThat(issues).onProperty("title").contains("public issue");
        assertThat(issues).onProperty("title").contains("private issue");
    }

    @Test
    public void assignee_findIssues_from_group() {
        // When
        List<Issue> issues = Search.findIssues("issue", assignee, labs, onePageFiveSize).getList();
        // Then
        assertThat(issues.size()).isEqualTo(2);
        assertThat(issues).onProperty("title").contains("public issue");
        assertThat(issues).onProperty("title").contains("private issue");
    }

    @Test
    public void anonymous_findIssues_from_public_project() {
        // When
        List<Issue> issues = Search.findIssues("issue", User.anonymous, publicProject, onePageFiveSize).getList();
        // Then
        assertThat(issues.size()).isEqualTo(1);
        assertThat(issues).onProperty("title").contains("public issue");
    }

    @Test
    public void anonymous_findIssues_from_protected_project() {
        // When
        List<Issue> issues = Search.findIssues("issue", User.anonymous, protectedProject, onePageFiveSize).getList();
        // Then
        assertThat(issues.size()).isEqualTo(0);
    }

    @Test
    public void anonymous_findIssues_from_private_project() {
        // When
        List<Issue> issues = Search.findIssues("issue", User.anonymous, privateProject, onePageFiveSize).getList();
        // Then
        assertThat(issues.size()).isEqualTo(0);
    }

    @Test
    public void projectMember_findIssues_from_public_project() {
        // When
        List<Issue> issues = Search.findIssues("issue", projectMember, publicProject, onePageFiveSize).getList();
        // Then
        assertThat(issues.size()).isEqualTo(1);
        assertThat(issues).onProperty("title").contains("public issue");
    }

    @Test
    public void projectMember_findIssues_from_protected_project() {
        // When
        List<Issue> issues = Search.findIssues("issue", projectMember, protectedProject, onePageFiveSize).getList();
        // Then
        assertThat(issues.size()).isEqualTo(0);
    }

    @Test
    public void projectMember_findIssues_from_private_project() {
        // When
        List<Issue> issues = Search.findIssues("issue", projectMember, privateProject, onePageFiveSize).getList();
        // Then
        assertThat(issues.size()).isEqualTo(1);
        assertThat(issues).onProperty("title").contains("private issue");
    }

    @Test
    public void groupMember_findIssues_from_public_project() {
        // When
        List<Issue> issues = Search.findIssues("issue", groupMember, publicProject, onePageFiveSize).getList();
        // Then
        assertThat(issues.size()).isEqualTo(1);
        assertThat(issues).onProperty("title").contains("public issue");
    }

    @Test
    public void groupMember_findIssues_from_protected_project() {
        // When
        List<Issue> issues = Search.findIssues("issue", groupMember, protectedProject, onePageFiveSize).getList();
        // Then
        assertThat(issues.size()).isEqualTo(1);
        assertThat(issues).onProperty("title").contains("protected issue");
    }

    @Test
    public void groupMember_findIssues_from_private_project() {
        // When
        List<Issue> issues = Search.findIssues("issue", groupMember, privateProject, onePageFiveSize).getList();
        // Then
        assertThat(issues.size()).isEqualTo(0);
    }

    @Test
    public void author_findIssues_from_public_project() {
        // When
        List<Issue> issues = Search.findIssues("issue", author, publicProject, onePageFiveSize).getList();
        // Then
        assertThat(issues.size()).isEqualTo(1);
        assertThat(issues).onProperty("title").contains("public issue");
    }

    @Test
    public void author_findIssues_from_protected_project() {
        // When
        List<Issue> issues = Search.findIssues("issue", author, protectedProject, onePageFiveSize).getList();
        // Then
        assertThat(issues.size()).isEqualTo(0);
    }

    @Test
    public void author_findIssues_from_private_project() {
        // When
        List<Issue> issues = Search.findIssues("issue", author, privateProject, onePageFiveSize).getList();
        // Then
        assertThat(issues.size()).isEqualTo(1);
        assertThat(issues).onProperty("title").contains("private issue");
    }

    @Test
    public void assignee_findIsses_from_public_project() {
        // When
        List<Issue> issues = Search.findIssues("issue", assignee, publicProject, onePageFiveSize).getList();
        // Then
        assertThat(issues.size()).isEqualTo(1);
        assertThat(issues).onProperty("title").contains("public issue");
    }

    @Test
    public void assignee_findIsses_from_protected_project() {
        // When
        List<Issue> issues = Search.findIssues("issue", assignee, protectedProject, onePageFiveSize).getList();
        // Then
        assertThat(issues.size()).isEqualTo(0);
    }

    @Test
    public void assignee_findIsses_from_private_project() {
        // When
        List<Issue> issues = Search.findIssues("issue", assignee, privateProject, onePageFiveSize).getList();
        // Then
        assertThat(issues.size()).isEqualTo(1);
        assertThat(issues).onProperty("title").contains("private issue");
    }

    /**
     * Find Milestones
     */

    @Test
    public void anonymous_findMilestone_from_all_repos() {
        // When
        List<Milestone> milestones = Search.findMilestones("milestone", User.anonymous, onePageFiveSize).getList();
        // Then
        assertThat(milestones.size()).isEqualTo(1);
        assertThat(milestones).onProperty("title").contains("public milestone");
    }

    @Test
    public void groupMember_findMilestone_from_all_repos() {
        // When
        List<Milestone> milestones = Search.findMilestones("milestone", groupMember, onePageFiveSize).getList();
        // Then
        assertThat(milestones.size()).isEqualTo(2);
        assertThat(milestones).onProperty("title").contains("public milestone");
        assertThat(milestones).onProperty("title").contains("protected milestone");
    }

    @Test
    public void projectMember_findMilestone_from_all_repos() {
        // When
        List<Milestone> milestones = Search.findMilestones("milestone", projectMember, onePageFiveSize).getList();
        // Then
        assertThat(milestones.size()).isEqualTo(2);
        assertThat(milestones).onProperty("title").contains("public milestone");
        assertThat(milestones).onProperty("title").contains("private milestone");
    }

    @Test
    public void projectAndGroupMember_findMilestone_from_all_repos() {
        // When
        List<Milestone> milestones = Search.findMilestones("milestone", groupAndProjectMember, onePageFiveSize).getList();
        // Then
        assertThat(milestones.size()).isEqualTo(3);
        assertThat(milestones).onProperty("title").contains("public milestone");
        assertThat(milestones).onProperty("title").contains("protected milestone");
        assertThat(milestones).onProperty("title").contains("private milestone");
    }

    @Test
    public void anonymous_findMilestone_from_public_project() {
        // When
        List<Milestone> milestones = Search.findMilestones("milestone", User.anonymous, publicProject, onePageFiveSize).getList();
        // Then
        assertThat(milestones.size()).isEqualTo(1);
        assertThat(milestones).onProperty("title").contains("public milestone");
    }

    @Test
    public void groupMember_findMilestone_from_public_project() {
        // When
        List<Milestone> milestones = Search.findMilestones("milestone", groupMember, publicProject, onePageFiveSize).getList();
        // Then
        assertThat(milestones.size()).isEqualTo(1);
        assertThat(milestones).onProperty("title").contains("public milestone");
    }

    @Test
    public void groupMember_findMilestone_from_protected_project() {
        // When
        List<Milestone> milestones = Search.findMilestones("milestone", groupMember, protectedProject, onePageFiveSize).getList();
        // Then
        assertThat(milestones.size()).isEqualTo(1);
        assertThat(milestones).onProperty("title").contains("protected milestone");
    }

    @Test
    public void projectMember_findMilestone_from_public_project() {
        // When
        List<Milestone> milestones = Search.findMilestones("milestone", projectMember, publicProject, onePageFiveSize).getList();
        // Then
        assertThat(milestones.size()).isEqualTo(1);
        assertThat(milestones).onProperty("title").contains("public milestone");
    }

    @Test
    public void projectMember_findMilestone_from_private_project() {
        // When
        List<Milestone> milestones = Search.findMilestones("milestone", projectMember, privateProject, onePageFiveSize).getList();
        // Then
        assertThat(milestones.size()).isEqualTo(1);
        assertThat(milestones).onProperty("title").contains("private milestone");
    }

    @Test
    public void projectAndGroupMember_findMilestone_from_public_project() {
        // When
        List<Milestone> milestones = Search.findMilestones("milestone", groupAndProjectMember, publicProject, onePageFiveSize).getList();
        // Then
        assertThat(milestones.size()).isEqualTo(1);
        assertThat(milestones).onProperty("title").contains("public milestone");
    }

    @Test
    public void projectAndGroupMember_findMilestone_from_protected_project() {
        // When
        List<Milestone> milestones = Search.findMilestones("milestone", groupAndProjectMember, protectedProject, onePageFiveSize).getList();
        // Then
        assertThat(milestones.size()).isEqualTo(1);
        assertThat(milestones).onProperty("title").contains("protected milestone");
    }

    @Test
    public void projectAndGroupMember_findMilestone_from_private_project() {
        // When
        List<Milestone> milestones = Search.findMilestones("milestone", groupAndProjectMember, privateProject, onePageFiveSize).getList();
        // Then
        assertThat(milestones.size()).isEqualTo(1);
        assertThat(milestones).onProperty("title").contains("private milestone");
    }

    @Test
    public void anonymous_findMilestone_from_group() {
        // When
        List<Milestone> milestones = Search.findMilestones("milestone", User.anonymous, labs, onePageFiveSize).getList();
        // Then
        assertThat(milestones.size()).isEqualTo(1);
        assertThat(milestones).onProperty("title").contains("public milestone");
    }

    @Test
    public void groupMember_findMilestone_from_group() {
        // When
        List<Milestone> milestones = Search.findMilestones("milestone", groupMember, labs, onePageFiveSize).getList();
        // Then
        assertThat(milestones.size()).isEqualTo(2);
        assertThat(milestones).onProperty("title").contains("public milestone");
        assertThat(milestones).onProperty("title").contains("protected milestone");
    }

    @Test
    public void projectMember_findMilestone_from_group() {
        // When
        List<Milestone> milestones = Search.findMilestones("milestone", projectMember, labs, onePageFiveSize).getList();
        // Then
        assertThat(milestones.size()).isEqualTo(2);
        assertThat(milestones).onProperty("title").contains("public milestone");
        assertThat(milestones).onProperty("title").contains("private milestone");
    }

    @Test
    public void projectAndGroupMember_findMilestone_from_group() {
        // When
        List<Milestone> milestones = Search.findMilestones("milestone", groupAndProjectMember, labs, onePageFiveSize).getList();
        // Then
        assertThat(milestones.size()).isEqualTo(3);
        assertThat(milestones).onProperty("title").contains("public milestone");
        assertThat(milestones).onProperty("title").contains("protected milestone");
        assertThat(milestones).onProperty("title").contains("private milestone");
    }

    /**
     * Issue Comments
     */

    @Test
    public void anonymous_findIssueComments_from_all_repos() {
        // When
        List<IssueComment> comments = Search.findIssueComments("comment", User.anonymous, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(1);
        assertThat(comments).onProperty("contents").contains("public comment");
    }

    @Test
    public void groupMember_findIssueComments_from_all_repos() {
        // When
        List<IssueComment> comments = Search.findIssueComments("comment", groupMember, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(2);
        assertThat(comments).onProperty("contents").contains("public comment");
        assertThat(comments).onProperty("contents").contains("protected comment");
    }

    @Test
    public void projectMember_findIssueComments_from_all_repos() {
        // When
        List<IssueComment> comments = Search.findIssueComments("comment", projectMember, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(2);
        assertThat(comments).onProperty("contents").contains("public comment");
        assertThat(comments).onProperty("contents").contains("private comment");
    }

    @Test
    public void projectAndGroupMember_findIssueComments_from_all_repos() {
        // When
        List<IssueComment> comments = Search.findIssueComments("comment", groupAndProjectMember, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(3);
        assertThat(comments).onProperty("contents").contains("public comment");
        assertThat(comments).onProperty("contents").contains("protected comment");
        assertThat(comments).onProperty("contents").contains("private comment");
    }

    @Test
    public void author_findIssueComments_from_all_repos() {
        // When
        List<IssueComment> comments = Search.findIssueComments("comment", author, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(2);
        assertThat(comments).onProperty("contents").contains("public comment");
        assertThat(comments).onProperty("contents").contains("private comment");
    }

    @Test
    public void anonymous_findIssueComments_from_public_project() {
        // When
        List<IssueComment> comments = Search.findIssueComments("comment", User.anonymous, publicProject, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(1);
        assertThat(comments).onProperty("contents").contains("public comment");
    }

    @Test
    public void anonymous_findIssueComments_from_protected_project() {
        // When
        List<IssueComment> comments = Search.findIssueComments("comment", User.anonymous, protectedProject, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(0);
    }

    @Test
    public void anonymous_findIssueComments_from_private_project() {
        // When
        List<IssueComment> comments = Search.findIssueComments("comment", User.anonymous, privateProject, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(0);
    }

    @Test
    public void groupMember_findIssueComments_from_public_project() {
        // When
        List<IssueComment> comments = Search.findIssueComments("comment", groupMember, publicProject, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(1);
        assertThat(comments).onProperty("contents").contains("public comment");
    }

    @Test
    public void groupMember_findIssueComments_from_protected_project() {
        // When
        List<IssueComment> comments = Search.findIssueComments("comment", groupMember, protectedProject, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(1);
        assertThat(comments).onProperty("contents").contains("protected comment");
    }

    @Test
    public void groupMember_findIssueComments_from_private_project() {
        // When
        List<IssueComment> comments = Search.findIssueComments("comment", groupMember, privateProject, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(0);
    }

    @Test
    public void projectMember_findIssueComments_from_public_project() {
        // When
        List<IssueComment> comments = Search.findIssueComments("comment", projectMember, publicProject, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(1);
        assertThat(comments).onProperty("contents").contains("public comment");
    }

    @Test
    public void projectMember_findIssueComments_from_protected_project() {
        // When
        List<IssueComment> comments = Search.findIssueComments("comment", projectMember, protectedProject, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(0);
    }

    @Test
    public void projectMember_findIssueComments_from_private_project() {
        // When
        List<IssueComment> comments = Search.findIssueComments("comment", projectMember, privateProject, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(1);
        assertThat(comments).onProperty("contents").contains("private comment");
    }

    @Test
    public void groupAndProjectMember_findIssueComments_from_public_project() {
        // When
        List<IssueComment> comments = Search.findIssueComments("comment", groupAndProjectMember, publicProject, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(1);
        assertThat(comments).onProperty("contents").contains("public comment");
    }

    @Test
    public void groupAndProjectMember_findIssueComments_from_protected_project() {
        // When
        List<IssueComment> comments = Search.findIssueComments("comment", groupAndProjectMember, protectedProject, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(1);
        assertThat(comments).onProperty("contents").contains("protected comment");
    }

    @Test
    public void groupAndProjectMember_findIssueComments_from_private_project() {
        // When
        List<IssueComment> comments = Search.findIssueComments("comment", groupAndProjectMember, privateProject, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(1);
        assertThat(comments).onProperty("contents").contains("private comment");
    }


    @Test
    public void author_findIssueComments_from_public_project() {
        // When
        List<IssueComment> comments = Search.findIssueComments("comment", author, publicProject, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(1);
        assertThat(comments).onProperty("contents").contains("public comment");
    }

    @Test
    public void author_findIssueComments_from_protected_project() {
        // When
        List<IssueComment> comments = Search.findIssueComments("comment", author, protectedProject, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(0);
    }

    @Test
    public void author_findIssueComments_from_private_project() {
        // When
        List<IssueComment> comments = Search.findIssueComments("comment", author, privateProject, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(1);
        assertThat(comments).onProperty("contents").contains("private comment");
    }

    @Test
    public void anonymous_findIssueComments_from_group() {
        // When
        List<IssueComment> comments = Search.findIssueComments("comment", User.anonymous, labs, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(1);
        assertThat(comments).onProperty("contents").contains("public comment");
    }

    @Test
    public void groupMember_findIssueComments_from_group() {
        // When
        List<IssueComment> comments = Search.findIssueComments("comment", groupMember, labs, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(2);
        assertThat(comments).onProperty("contents").contains("public comment");
        assertThat(comments).onProperty("contents").contains("protected comment");
    }

    @Test
    public void projectMember_findIssueComments_from_group() {
        // When
        List<IssueComment> comments = Search.findIssueComments("comment", projectMember, labs, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(2);
        assertThat(comments).onProperty("contents").contains("public comment");
        assertThat(comments).onProperty("contents").contains("private comment");
    }

    @Test
    public void groupAndProjectMember_findIssueComments_from_group() {
        // When
        List<IssueComment> comments = Search.findIssueComments("comment", groupAndProjectMember, labs, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(3);
        assertThat(comments).onProperty("contents").contains("public comment");
        assertThat(comments).onProperty("contents").contains("protected comment");
        assertThat(comments).onProperty("contents").contains("private comment");
    }

    @Test
    public void author_findIssueComments_from_group() {
        // When
        List<IssueComment> comments = Search.findIssueComments("comment", author, labs, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(2);
        assertThat(comments).onProperty("contents").contains("public comment");
        assertThat(comments).onProperty("contents").contains("private comment");
    }

    /**
     * Find PostComments
     */

    @Test
    public void anonymois_findPostComments_from_all_repos() {
        // When
        List<PostingComment> comments = Search.findPostComments("comment", User.anonymous, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(1);
        assertThat(comments).onProperty("contents").contains("public comment");
    }

    @Test
    public void groupMember_findPostComments_from_all_repos() {
        // When
        List<PostingComment> comments = Search.findPostComments("comment", groupMember, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(2);
        assertThat(comments).onProperty("contents").contains("public comment");
        assertThat(comments).onProperty("contents").contains("protected comment");
    }

    @Test
    public void projectMember_findPostComments_from_all_repos() {
        // When
        List<PostingComment> comments = Search.findPostComments("comment", projectMember, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(2);
        assertThat(comments).onProperty("contents").contains("public comment");
        assertThat(comments).onProperty("contents").contains("private comment");
    }

    @Test
    public void projectAndGroupMember_findPostComments_from_all_repos() {
        // When
        List<PostingComment> comments = Search.findPostComments("comment", groupAndProjectMember, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(3);
        assertThat(comments).onProperty("contents").contains("public comment");
        assertThat(comments).onProperty("contents").contains("protected comment");
        assertThat(comments).onProperty("contents").contains("private comment");
    }

    @Test
    public void author_findPostComments_from_all_repos() {
        // When
        List<PostingComment> comments = Search.findPostComments("comment", author, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(2);
        assertThat(comments).onProperty("contents").contains("public comment");
        assertThat(comments).onProperty("contents").contains("private comment");
    }

    @Test
    public void anonymous_findPostComments_from_public_project() {
        // When
        List<PostingComment> comments = Search.findPostComments("comment", User.anonymous, publicProject, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(1);
        assertThat(comments).onProperty("contents").contains("public comment");
    }

    @Test
    public void anonymous_findPostComments_from_protected_project() {
        // When
        List<PostingComment> comments = Search.findPostComments("comment", User.anonymous, protectedProject, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(0);
    }

    @Test
    public void anonymous_findPostComments_from_private_project() {
        // When
        List<PostingComment> comments = Search.findPostComments("comment", User.anonymous, privateProject, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(0);
    }

    @Test
    public void groupMember_findPostComments_from_public_project() {
        // When
        List<PostingComment> comments = Search.findPostComments("comment", groupMember, publicProject, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(1);
        assertThat(comments).onProperty("contents").contains("public comment");
    }

    @Test
    public void groupMember_findPostComments_from_protected_project() {
        // When
        List<PostingComment> comments = Search.findPostComments("comment", groupMember, protectedProject, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(1);
        assertThat(comments).onProperty("contents").contains("protected comment");
    }

    @Test
    public void groupMember_findPostComments_from_private_project() {
        // When
        List<PostingComment> comments = Search.findPostComments("comment", groupMember, privateProject, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(0);
    }

    @Test
    public void projectMember_findPostComments_from_public_project() {
        // When
        List<PostingComment> comments = Search.findPostComments("comment", projectMember, publicProject, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(1);
        assertThat(comments).onProperty("contents").contains("public comment");
    }

    @Test
    public void projectMember_findPostComments_from_protected_project() {
        // When
        List<PostingComment> comments = Search.findPostComments("comment", projectMember, protectedProject, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(0);
    }

    @Test
    public void projectMember_findPostComments_from_private_project() {
        // When
        List<PostingComment> comments = Search.findPostComments("comment", projectMember, privateProject, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(1);
        assertThat(comments).onProperty("contents").contains("private comment");
    }

    @Test
    public void groupAndProjectMember_findPostComments_from_public_project() {
        // When
        List<PostingComment> comments = Search.findPostComments("comment", groupAndProjectMember, publicProject, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(1);
        assertThat(comments).onProperty("contents").contains("public comment");
    }

    @Test
    public void groupAndProjectMember_findPostComments_from_protected_project() {
        // When
        List<PostingComment> comments = Search.findPostComments("comment", groupAndProjectMember, protectedProject, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(1);
        assertThat(comments).onProperty("contents").contains("protected comment");
    }

    @Test
    public void groupAndProjectMember_findPostComments_from_private_project() {
        // When
        List<PostingComment> comments = Search.findPostComments("comment", groupAndProjectMember, privateProject, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(1);
        assertThat(comments).onProperty("contents").contains("private comment");
    }


    @Test
    public void author_findPostComments_from_public_project() {
        // When
        List<PostingComment> comments = Search.findPostComments("comment", author, publicProject, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(1);
        assertThat(comments).onProperty("contents").contains("public comment");
    }

    @Test
    public void author_findPostComments_from_protected_project() {
        // When
        List<PostingComment> comments = Search.findPostComments("comment", author, protectedProject, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(0);
    }

    @Test
    public void author_findPostComments_from_private_project() {
        // When
        List<PostingComment> comments = Search.findPostComments("comment", author, privateProject, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(1);
        assertThat(comments).onProperty("contents").contains("private comment");
    }

    @Test
    public void anonymous_findPostComments_from_group() {
        // When
        List<PostingComment> comments = Search.findPostComments("comment", User.anonymous, labs, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(1);
        assertThat(comments).onProperty("contents").contains("public comment");
    }

    @Test
    public void groupMember_findPostComments_from_group() {
        // When
        List<PostingComment> comments = Search.findPostComments("comment", groupMember, labs, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(2);
        assertThat(comments).onProperty("contents").contains("public comment");
        assertThat(comments).onProperty("contents").contains("protected comment");
    }

    @Test
    public void projectMember_findPostComments_from_group() {
        // When
        List<PostingComment> comments = Search.findPostComments("comment", projectMember, labs, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(2);
        assertThat(comments).onProperty("contents").contains("public comment");
        assertThat(comments).onProperty("contents").contains("private comment");
    }

    @Test
    public void groupAndProjectMember_findPostComments_from_group() {
        // When
        List<PostingComment> comments = Search.findPostComments("comment", groupAndProjectMember, labs, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(3);
        assertThat(comments).onProperty("contents").contains("public comment");
        assertThat(comments).onProperty("contents").contains("protected comment");
        assertThat(comments).onProperty("contents").contains("private comment");
    }

    @Test
    public void author_findPostComments_from_group() {
        // When
        List<PostingComment> comments = Search.findPostComments("comment", author, labs, onePageFiveSize).getList();
        // Then
        assertThat(comments.size()).isEqualTo(2);
        assertThat(comments).onProperty("contents").contains("public comment");
        assertThat(comments).onProperty("contents").contains("private comment");
    }

    /**
     * Find Reviews
     */

    @Test
    public void anonymous_findReviews_from_all_repos() {
        // When
        List<ReviewComment> reviews = Search.findReviews("review", User.anonymous, onePageFiveSize).getList();
        // Then
        assertThat(reviews.size()).isEqualTo(1);
        assertThat(reviews).onProperty("contents").contains("public review");
    }

    @Test
    public void groupMember_findReviews_from_all_repos() {
        // When
        List<ReviewComment> reviews = Search.findReviews("review", groupMember, onePageFiveSize).getList();
        // Then
        assertThat(reviews.size()).isEqualTo(2);
        assertThat(reviews).onProperty("contents").contains("public review");
        assertThat(reviews).onProperty("contents").contains("protected review");
    }

    @Test
    public void projectMember_findReviews_from_all_repos() {
        // When
        List<ReviewComment> reviews = Search.findReviews("review", projectMember, onePageFiveSize).getList();
        // Then
        assertThat(reviews.size()).isEqualTo(2);
        assertThat(reviews).onProperty("contents").contains("public review");
        assertThat(reviews).onProperty("contents").contains("private review");
    }

    @Test
    public void projectAndGroupMember_findReviews_from_all_repos() {
        // When
        List<ReviewComment> reviews = Search.findReviews("review", groupAndProjectMember, onePageFiveSize).getList();
        // Then
        assertThat(reviews.size()).isEqualTo(3);
        assertThat(reviews).onProperty("contents").contains("public review");
        assertThat(reviews).onProperty("contents").contains("protected review");
        assertThat(reviews).onProperty("contents").contains("private review");
    }

    @Test
    public void author_findReviews_from_all_repos() {
        // When
        List<ReviewComment> reviews = Search.findReviews("review", author, onePageFiveSize).getList();
        // Then
        assertThat(reviews.size()).isEqualTo(2);
        assertThat(reviews).onProperty("contents").contains("public review");
        assertThat(reviews).onProperty("contents").contains("private review");
    }

    @Test
    public void anonymous_findReviews_from_public_project() {
        // When
        List<ReviewComment> reviews = Search.findReviews("review", User.anonymous, publicProject, onePageFiveSize).getList();
        // Then
        assertThat(reviews.size()).isEqualTo(1);
        assertThat(reviews).onProperty("contents").contains("public review");
    }

    @Test
    public void anonymous_findReviews_from_protected_project() {
        // When
        List<ReviewComment> reviews = Search.findReviews("review", User.anonymous, protectedProject, onePageFiveSize).getList();
        // Then
        assertThat(reviews.size()).isEqualTo(0);
    }

    @Test
    public void anonymous_findReviews_from_private_project() {
        // When
        List<ReviewComment> reviews = Search.findReviews("review", User.anonymous, privateProject, onePageFiveSize).getList();
        // Then
        assertThat(reviews.size()).isEqualTo(0);
    }

    @Test
    public void groupMember_findReviews_from_public_project() {
        // When
        List<ReviewComment> reviews = Search.findReviews("review", groupMember, publicProject, onePageFiveSize).getList();
        // Then
        assertThat(reviews.size()).isEqualTo(1);
        assertThat(reviews).onProperty("contents").contains("public review");
    }

    @Test
    public void groupMember_findReviews_from_protected_project() {
        // When
        List<ReviewComment> reviews = Search.findReviews("review", groupMember, protectedProject, onePageFiveSize).getList();
        // Then
        assertThat(reviews.size()).isEqualTo(1);
        assertThat(reviews).onProperty("contents").contains("protected review");
    }

    @Test
    public void groupMember_findReviews_from_private_project() {
        // When
        List<ReviewComment> reviews = Search.findReviews("review", groupMember, privateProject, onePageFiveSize).getList();
        // Then
        assertThat(reviews.size()).isEqualTo(0);
    }

    @Test
    public void projectMember_findReviews_from_public_project() {
        // When
        List<ReviewComment> reviews = Search.findReviews("review", projectMember, publicProject, onePageFiveSize).getList();
        // Then
        assertThat(reviews.size()).isEqualTo(1);
        assertThat(reviews).onProperty("contents").contains("public review");
    }

    @Test
    public void projectMember_findReviews_from_protected_project() {
        // When
        List<ReviewComment> reviews = Search.findReviews("review", projectMember, protectedProject, onePageFiveSize).getList();
        // Then
        assertThat(reviews.size()).isEqualTo(0);
    }

    @Test
    public void projectMember_findReviews_from_private_project() {
        // When
        List<ReviewComment> reviews = Search.findReviews("review", projectMember, privateProject, onePageFiveSize).getList();
        // Then
        assertThat(reviews.size()).isEqualTo(1);
        assertThat(reviews).onProperty("contents").contains("private review");
    }

    @Test
    public void groupAndProjectMember_findReviews_from_public_project() {
        // When
        List<ReviewComment> reviews = Search.findReviews("review", groupAndProjectMember, publicProject, onePageFiveSize).getList();
        // Then
        assertThat(reviews.size()).isEqualTo(1);
        assertThat(reviews).onProperty("contents").contains("public review");
    }

    @Test
    public void groupAndProjectMember_findReviews_from_protected_project() {
        // When
        List<ReviewComment> reviews = Search.findReviews("review", groupAndProjectMember, protectedProject, onePageFiveSize).getList();
        // Then
        assertThat(reviews.size()).isEqualTo(1);
        assertThat(reviews).onProperty("contents").contains("protected review");
    }

    @Test
    public void groupAndProjectMember_findReviews_from_private_project() {
        // When
        List<ReviewComment> reviews = Search.findReviews("review", groupAndProjectMember, privateProject, onePageFiveSize).getList();
        // Then
        assertThat(reviews.size()).isEqualTo(1);
        assertThat(reviews).onProperty("contents").contains("private review");
    }


    @Test
    public void author_findReviews_from_public_project() {
        // When
        List<ReviewComment> reviews = Search.findReviews("review", author, publicProject, onePageFiveSize).getList();
        // Then
        assertThat(reviews.size()).isEqualTo(1);
        assertThat(reviews).onProperty("contents").contains("public review");
    }

    @Test
    public void author_findReviews_from_protected_project() {
        // When
        List<ReviewComment> reviews = Search.findReviews("review", author, protectedProject, onePageFiveSize).getList();
        // Then
        assertThat(reviews.size()).isEqualTo(0);
    }

    @Test
    public void author_findReviews_from_private_project() {
        // When
        List<ReviewComment> reviews = Search.findReviews("review", author, privateProject, onePageFiveSize).getList();
        // Then
        assertThat(reviews.size()).isEqualTo(1);
        assertThat(reviews).onProperty("contents").contains("private review");
    }

    @Test
    public void anonymous_findReviews_from_group() {
        // When
        List<ReviewComment> reviews = Search.findReviews("review", User.anonymous, labs, onePageFiveSize).getList();
        // Then
        assertThat(reviews.size()).isEqualTo(1);
        assertThat(reviews).onProperty("contents").contains("public review");
    }

    @Test
    public void groupMember_findReviews_from_group() {
        // When
        List<ReviewComment> reviews = Search.findReviews("review", groupMember, labs, onePageFiveSize).getList();
        // Then
        assertThat(reviews.size()).isEqualTo(2);
        assertThat(reviews).onProperty("contents").contains("public review");
        assertThat(reviews).onProperty("contents").contains("protected review");
    }

    @Test
    public void projectMember_findReviews_from_group() {
        // When
        List<ReviewComment> reviews = Search.findReviews("review", projectMember, labs, onePageFiveSize).getList();
        // Then
        assertThat(reviews.size()).isEqualTo(2);
        assertThat(reviews).onProperty("contents").contains("public review");
        assertThat(reviews).onProperty("contents").contains("private review");
    }

    @Test
    public void groupAndProjectMember_findReviews_from_group() {
        // When
        List<ReviewComment> reviews = Search.findReviews("review", groupAndProjectMember, labs, onePageFiveSize).getList();
        // Then
        assertThat(reviews.size()).isEqualTo(3);
        assertThat(reviews).onProperty("contents").contains("public review");
        assertThat(reviews).onProperty("contents").contains("protected review");
        assertThat(reviews).onProperty("contents").contains("private review");
    }

    @Test
    public void author_findReviews_from_group() {
        // When
        List<ReviewComment> reviews = Search.findReviews("review", author, labs, onePageFiveSize).getList();
        // Then
        assertThat(reviews.size()).isEqualTo(2);
        assertThat(reviews).onProperty("contents").contains("public review");
        assertThat(reviews).onProperty("contents").contains("private review");
    }

}
