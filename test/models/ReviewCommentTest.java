package models;

import models.enumeration.Operation;
import org.junit.Before;
import org.junit.Test;
import utils.JodaDateUtil;
import utils.AccessControl;

import java.util.ArrayList;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

public class ReviewCommentTest extends ModelTest<ReviewComment> {
    private User admin;
    private User manager;
    private User member;
    private User threadAuthor;
    private User author;
    private User nonmember;
    private User anonymous;
    private ReviewComment comment;
    private CommentThread thread;
    private Project project;

    @Test
    public void findByThread() {
        // given
        List<Long> ids = addTestData();
        Long idOfThread1 = ids.get(0);
        CommentThread thread = CommentThread.find.byId(idOfThread1);
        assertThat(thread).isNotNull();

        // when
        List<ReviewComment> commentList = ReviewComment.findByThread(idOfThread1);

        // then
        assertThat(commentList.size()).isEqualTo(2);
        assertThat(commentList.get(0).id).isEqualTo(ids.get(2));
        assertThat(commentList.get(1).id).isEqualTo(ids.get(1));


        /* Test comments in CodeCommentThread */

        // given
        Long idOfThread2 = ids.get(3);
        thread = CommentThread.find.byId(idOfThread2);
        assertThat(thread).isNotNull();

        // when
        commentList = ReviewComment.findByThread(idOfThread2);

        // then
        assertThat(commentList.size()).isEqualTo(1);
        assertThat(commentList.get(0).id).isEqualTo(ids.get(4));
    }

    /**
     * {@code commitId}를 가지는 {@link models.NonRangedCodeCommentThread} 한 개 저장.
     *  - state: OPEN
     *  - createDate: 3일전
     * {@link models.ReviewComment} 생성하여 {@link models.NonRangedCodeCommentThread}에 추가.
     *  - createdDate: 3일전
     * {@link models.ReviewComment} 생성하여 {@link models.NonRangedCodeCommentThread}에 추가.
     *  - createdDate: 2일전
     * {@code commitId}를 가지는 {@link models.CodeCommentThread} 한 개 저장.
     *  - state: CLOSED
     *  - path: "readme.md"
     *  - createdDate: 2일전
     * {@code }
     * {@link models.ReviewComment} 생성하여 {@link models.CodeCommentThread}에 추가.
     *  - createdDate: 3일전
     *
     *  @return {thread1.id, comment1.id, comment2.id, thread2.id, comment3.id}
     */
    private List<Long> addTestData() {
        List<Long> ids = new ArrayList<>();

        NonRangedCodeCommentThread thread1 = new NonRangedCodeCommentThread();
        thread1.project = project;
        thread1.commitId = "123456";
        thread1.state = CommentThread.ThreadState.OPEN;
        thread1.createdDate = JodaDateUtil.before(3);
        thread1.save();
        ids.add(thread1.id);

        ReviewComment reviewComment1 = new ReviewComment();
        thread1.addComment(reviewComment1);
        reviewComment1.createdDate = JodaDateUtil.before(3);
        reviewComment1.setContents("reviewComment1");
        reviewComment1.save();
        ids.add(reviewComment1.id);

        ReviewComment reviewComment2 = new ReviewComment();
        thread1.addComment(reviewComment2);
        reviewComment2.createdDate = JodaDateUtil.before(2);
        reviewComment2.setContents("reviewComment3");
        reviewComment2.save();
        ids.add(reviewComment2.id);

        CodeCommentThread thread2 = new CodeCommentThread();
        thread2.project = project;
        thread2.commitId = "123456";
        thread2.state = CommentThread.ThreadState.CLOSED;
        CodeRange codeRange = new CodeRange();
        codeRange.path = "readme.md";
        codeRange.startColumn = 0;
        codeRange.startLine = 1;
        codeRange.startSide = CodeRange.Side.A;
        codeRange.endColumn = 100;
        codeRange.endLine = 10;
        codeRange.endSide = CodeRange.Side.B;
        thread2.codeRange = codeRange;
        thread2.createdDate = JodaDateUtil.before(2);
        thread2.save();
        ids.add(thread2.id);

        ReviewComment reviewComment3 = new ReviewComment();
        reviewComment3.thread = thread2;
        reviewComment3.createdDate = JodaDateUtil.before(2);
        reviewComment3.setContents("reviewComment2");
        reviewComment3.save();
        ids.add(reviewComment3.id);

        return ids;
    }

    @Before
    public void before() {
        project = Project.findByOwnerAndProjectName("yobi", "projectYobi");
        admin = User.findByLoginId("admin");
        manager = User.findByLoginId("yobi");
        member = User.findByLoginId("laziel");
        author = User.findByLoginId("nori");
        threadAuthor = User.findByLoginId("alecsiel");
        nonmember = User.findByLoginId("doortts");
        anonymous = new NullUser();

        thread = new SimpleCommentThread();
        thread.project = project;
        thread.author = new UserIdent(threadAuthor);
        thread.state = SimpleCommentThread.ThreadState.OPEN;
        thread.save();

        comment = new ReviewComment();
        comment.thread = thread;
        comment.author = new UserIdent(author);
        comment.save();

        assertThat(this.admin.isSiteManager()).describedAs("admin is Site Admin.").isTrue();
        assertThat(ProjectUser.isManager(manager.id, project.id)).describedAs("manager is a manager").isTrue();
        assertThat(ProjectUser.isManager(member.id, project.id)).describedAs("member is not a manager").isFalse();
        assertThat(ProjectUser.isMember(member.id, project.id)).describedAs("member is a member").isTrue();
        assertThat(ProjectUser.isMember(author.id, project.id)).describedAs("author is not a member").isFalse();
        assertThat(ProjectUser.isMember(threadAuthor.id, project.id)).describedAs("threadAuthor is not a member").isFalse();
        assertThat(project.isPublic).isTrue();
    }

    @Test
    public void editByAuthor() {
        assertThat(AccessControl.isAllowed(author, comment.asResource(), Operation.UPDATE)).isTrue();
    }

    @Test
    public void editByThreadAuthor() {
        assertThat(AccessControl.isAllowed(threadAuthor, comment.asResource(),
                Operation.UPDATE)).isFalse();
    }

    @Test
    public void editBySiteAdmin() {
        assertThat(AccessControl.isAllowed(admin, comment.asResource(), Operation.UPDATE)).isTrue();
    }

    @Test
    public void editByManager() {
        assertThat(AccessControl.isAllowed(member, comment.asResource(),
                Operation.UPDATE)).isTrue();
    }

    @Test
    public void editByMember() {
        assertThat(AccessControl.isAllowed(member, comment.asResource(),
                Operation.UPDATE)).isTrue();
    }

    @Test
    public void editByNonmember() {
        assertThat(AccessControl.isAllowed(nonmember, comment.asResource(),
                Operation.UPDATE)).isFalse();
    }

    @Test
    public void editByAnonymous() {
        assertThat(AccessControl.isAllowed(anonymous, comment.asResource(),
                Operation.UPDATE)).isFalse();
    }

    @Test
    public void deleteByAuthor() {
        assertThat(AccessControl.isAllowed(author, comment.asResource(), Operation.DELETE)).isTrue();
    }

    @Test
    public void deleteByThreadAuthor() {
        assertThat(AccessControl.isAllowed(threadAuthor, comment.asResource(),
                Operation.DELETE)).isFalse();
    }

    @Test
    public void deleteBySiteAdmin() {
        assertThat(AccessControl.isAllowed(admin, comment.asResource(), Operation.DELETE)).isTrue();
    }

    @Test
    public void deleteByManager() {
        assertThat(AccessControl.isAllowed(member, comment.asResource(),
                Operation.DELETE)).isTrue();
    }

    @Test
    public void deleteByMember() {
        assertThat(AccessControl.isAllowed(member, comment.asResource(),
                Operation.DELETE)).isTrue();
    }

    @Test
    public void deleteByNonmember() {
        assertThat(AccessControl.isAllowed(nonmember, comment.asResource(),
                Operation.DELETE)).isFalse();
    }

    @Test
    public void deleteByAnonymous() {
        assertThat(AccessControl.isAllowed(anonymous, comment.asResource(),
                Operation.DELETE)).isFalse();
    }

}
