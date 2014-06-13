/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Wansoon Park
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package models;

import models.enumeration.Operation;
import models.enumeration.ProjectScope;

import org.junit.Before;
import org.junit.Test;

import utils.JodaDateUtil;
import utils.AccessControl;

import java.util.ArrayList;
import java.util.Date;
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
        // when
        List<ReviewComment> commentList = ReviewComment.findByThread(thread.id);

        // then
        assertThat(commentList.size()).isEqualTo(1);
        assertThat(commentList.get(0).id).isEqualTo(comment.id);
    }

    @Test
    public void saveReviewComment() {
        // given
        CommentThread thread = createTestThread();
        createTestReviewComment(thread, "리뷰댓글");

        // when
        List<ReviewComment> savedReviewCommentList = ReviewComment.findByThread(thread.id);
        CommentThread savedThread = CommentThread.find.byId(thread.id);

        // then
        assertThat(savedReviewCommentList.size()).isEqualTo(1);
        assertThat(savedThread.reviewComments.size()).isEqualTo(1);
    }

    @Test
    public void deleteReviewComment() {
        // given
        CommentThread thread = createTestThread();
        ReviewComment firstReviewComment = createTestReviewComment(thread, "첫번째");
        ReviewComment secondReviewComment = createTestReviewComment(thread, "두번째");
        long threadId = thread.id;

        // when
        firstReviewComment.delete();

        // then
        assertThat(ReviewComment.find.byId(firstReviewComment.id)).isNull();
        assertThat(CommentThread.find.byId(threadId)).isNotNull();
        assertThat(CommentThread.find.byId(threadId).reviewComments.size()).isEqualTo(1);
    }

    @Test
    public void deleteLastReviewComment() {
        // given
        ReviewComment reviewComment = createTestReviewComment(createTestThread(), "리뷰댓글");
        ReviewComment lastReviewComment = ReviewComment.find.byId(reviewComment.id);
        long threadId = lastReviewComment.thread.id;

        // when
        lastReviewComment.delete();

        // then
        assertThat(ReviewComment.find.byId(reviewComment.id)).isNull();
        assertThat(CommentThread.find.byId(threadId)).isNull();
    }


    private SimpleCommentThread createTestThread() {
        User author = new User();
        author.id = 1L;
        author.loginId = "tesT";
        author.name = "이름";

        UserIdent userIdent = new UserIdent(author);

        SimpleCommentThread thread = new SimpleCommentThread();
        thread.project = Project.find.byId(1L);
        thread.state = CommentThread.ThreadState.OPEN;
        thread.author = userIdent;
        thread.save();

        return thread;
    }

    private ReviewComment createTestReviewComment(CommentThread thread, String contents) {
        ReviewComment reviewComment = new ReviewComment();
        reviewComment.setContents(contents);
        reviewComment.author = thread.author;
        reviewComment.createdDate = new Date();
        thread.addComment(reviewComment);
        reviewComment.save();

        return reviewComment;
    }


    /**
     * Adds test data.
     *
     * A {@link models.NonRangedCodeCommentThread} having {@code commitId} is saved.
     *  - state: OPEN
     *  - createDate: 3 days ago
     * A {@link models.ReviewComment} is created and added to the {@link models.NonRangedCodeCommentThread}.
     *  - createdDate: 3 days ago
     * A {@link models.ReviewComment} is created and added to the {@link models.NonRangedCodeCommentThread}.
     *  - createdDate: 2 days ago
     * A {@link models.CodeCommentThread} having the {@code commitId} is saved.
     *  - state: CLOSED
     *  - path: "readme.md"
     *  - createdDate: 2 days ago
     * A {@link models.ReviewComment} is created and added to {@link models.CodeCommentThread}.
     *  - createdDate: 3 days ago
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
        thread.addComment(comment);
        comment.author = new UserIdent(author);
        comment.save();

        assertThat(this.admin.isSiteManager()).describedAs("admin is Site Admin.").isTrue();
        assertThat(ProjectUser.isManager(manager.id, project.id)).describedAs("manager is a manager").isTrue();
        assertThat(ProjectUser.isManager(member.id, project.id)).describedAs("member is not a manager").isFalse();
        assertThat(ProjectUser.isMember(member.id, project.id)).describedAs("member is a member").isTrue();
        assertThat(ProjectUser.isMember(author.id, project.id)).describedAs("author is not a member").isFalse();
        assertThat(ProjectUser.isMember(threadAuthor.id, project.id)).describedAs("threadAuthor is not a member").isFalse();
        assertThat(project.projectScope).isEqualTo(ProjectScope.PUBLIC);
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
