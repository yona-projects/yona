/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Changsung Kim
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
package models.support;

import models.*;
import org.junit.*;
import java.util.List;
import play.test.FakeApplication;
import play.test.Helpers;
import utils.JodaDateUtil;
import controllers.ProjectApp;
import static org.fest.assertions.Assertions.assertThat;

/**
 * The class to test {@link models.support.ReviewSearchCondition}
 */
public class ReviewSearchConditionTest extends ModelTest<ReviewSearchCondition> {

    CommentThread ct1;
    CommentThread ct2;
    CommentThread ct3;

    @Before
    public void before() {
        addTestData();
    }

    @After
    public void after() {
        clearData();
    }


    /**
     * Tests searching reviews having the content.
     */
    @Test
    public void filterForComment() {
        // given
        Project project = Project.findByOwnerAndProjectName("yobi", "projectYobi");
        ReviewSearchCondition searchCondition = new ReviewSearchCondition();
        searchCondition.filter = "111";
        searchCondition.state = CommentThread.ThreadState.OPEN.name();

        // when
        List<CommentThread> expressionList = searchCondition.asExpressionList(project).findList();

        // then
        assertThat(expressionList.size()).isEqualTo(1);
        assertThat(expressionList.get(0).reviewComments.get(0).getContents().contains("111"));
    }

    /**
     * Tests searching reviews written to the commit with commit id.
     */
    @Test
    public void filterForCommitId() {
        // given
        String testCommitId = "300";
        Project project = Project.findByOwnerAndProjectName("yobi", "projectYobi");
        ReviewSearchCondition searchCondition = new ReviewSearchCondition();
        searchCondition.filter = testCommitId;
        searchCondition.state = CommentThread.ThreadState.OPEN.name();

        // when
        List<CommentThread> expressionList = searchCondition.asExpressionList(project).findList();

        // then
        assertThat(expressionList.size()).isEqualTo(1);
        CodeCommentThread thread = (CodeCommentThread)expressionList.get(0);
        assertThat(thread.commitId).contains(testCommitId);
    }

    /**
     * Tests searching reviews written to the file
     */
    @Test
    public void filterForPath() {
        // given
        String testPath = "/app/controllers/IssueApp.java";
        Project project = Project.findByOwnerAndProjectName("yobi", "projectYobi");
        ReviewSearchCondition searchCondition = new ReviewSearchCondition();
        searchCondition.filter = testPath;
        searchCondition.state = CommentThread.ThreadState.OPEN.name();

        // when
        List<CommentThread> expressionList = searchCondition.asExpressionList(project).findList();

        // then
        assertThat(expressionList.size()).isEqualTo(1);
        CodeCommentThread thread = (CodeCommentThread)expressionList.get(0);
        assertThat(thread.codeRange.path).contains(testPath);
    }

    /**
     * Tests searching reviews for contents, commit id and file path.
     */
    @Test
    public void filterForAll() {
        // given
        String testFilter = "controllers";
        Project project = Project.findByOwnerAndProjectName("yobi", "projectYobi");
        ReviewSearchCondition searchCondition = new ReviewSearchCondition();
        searchCondition.filter = testFilter;
        searchCondition.state = CommentThread.ThreadState.OPEN.name();

        // when
        List<CommentThread> expressionList = searchCondition.asExpressionList(project).findList();

        // then
        assertThat(expressionList.size()).isEqualTo(3);
    }

    /**
     * Tests searching reviews written by the user specified.
     */
    @Test
    public void searchingAuthor() {
        // given
        Project project = Project.findByOwnerAndProjectName("yobi", "projectYobi");
        ReviewSearchCondition searchCondition = new ReviewSearchCondition();
        searchCondition.authorId = User.findByLoginId("admin").id;
        searchCondition.state = CommentThread.ThreadState.OPEN.name();

        // when
        List<CommentThread> expressionList = searchCondition.asExpressionList(project).findList();

        // then
        assertThat(expressionList.size()).isEqualTo(2);
    }

    /**
     * Tests searching reviews which the user specified participated in.
     */
    @Test
    public void searchingParticipant() {
        // given
        Project project = Project.findByOwnerAndProjectName("yobi", "projectYobi");
        ReviewSearchCondition searchCondition = new ReviewSearchCondition();
        searchCondition.participantId = User.findByLoginId("admin").id;
        searchCondition.state = CommentThread.ThreadState.OPEN.name();

        // when
        List<CommentThread> expressionList = searchCondition.asExpressionList(project).findList();

        // then
        assertThat(expressionList.size()).isEqualTo(2);
    }

    /**
     * Adds test data.
     */
    private void addTestData() {
        User adminUser = User.findByLoginId("admin");
        User lazielUser = User.findByLoginId("laziel");
        Project project = Project.findByOwnerAndProjectName("yobi", "projectYobi");

        ct1 = addTestThread1(adminUser, lazielUser, project);
        ct2 = addTestThread2(adminUser, lazielUser, project);
        ct3 = addTestThread3(adminUser, lazielUser, project);
    }

    private void clearData() {
        ct1.delete();
        ct2.delete();
        ct3.delete();
    }

    private CommentThread addTestThread1(User admin, User user, Project project) {
        CodeCommentThread thread = new CodeCommentThread();
        thread.createdDate = JodaDateUtil.today();
        thread.commitId = "controllers";
        thread.state = CommentThread.ThreadState.OPEN;
        makeThread(admin, "Comment #1 : 111", project, thread);
        makeComment(thread, user, "Comment #1-1");
        makeComment(thread, user, "Comment #1-2");
        return thread;
    }

    private CommentThread addTestThread2(User admin, User user, Project project) {
        CodeCommentThread thread = new CodeCommentThread();
        thread.createdDate = JodaDateUtil.before(2);
        thread.commitId = "200";
        thread.state = CommentThread.ThreadState.OPEN;
        makeThread(admin, "Comment #2 : /app/controllers/BoardApp.java", project, thread);
        makeComment(thread, admin, "Comment #2-1");
        makeComment(thread, user, "Comment #2-2");
        return thread;
    }

    private CommentThread addTestThread3(User admin, User user, Project project) {
        CodeCommentThread thread = new CodeCommentThread();
        thread.createdDate = JodaDateUtil.before(3);
        thread.commitId = "300";
        thread.state = CommentThread.ThreadState.OPEN;
        thread.codeRange.path = "/app/controllers/IssueApp.java";
        makeThread(user, "Comment #3", project, thread);
        makeComment(thread, user, "Comment #3-1");
        makeComment(thread, user, "Comment #3-2");
        makeComment(thread, user, "Comment #3-3");
        return thread;
    }
    /**
     * Creates a comment and added to {@code thread}
     * @param thread
     * @param author
     * @param contents
     * @return
     */
    private ReviewComment makeComment(CommentThread thread, User author, String contents) {
        ReviewComment reviewComment = new ReviewComment();
        reviewComment.createdDate = JodaDateUtil.now();
        reviewComment.author = new UserIdent(author);
        reviewComment.thread = thread;
        reviewComment.setContents(contents);
        reviewComment.save();

        return reviewComment;
    }

    /**
     * Creates a thread.
     * @param author
     * @param contents
     * @param project
     * @param thread
     * @return
     */
    private void makeThread(User author, String contents, Project project, CommentThread thread) {
        thread.project = project;
        thread.author = new UserIdent(author);
        thread.save();

        ReviewComment reviewComment = new ReviewComment();
        reviewComment.createdDate = thread.createdDate;
        reviewComment.author = thread.author;
        reviewComment.thread = thread;
        reviewComment.setContents(contents);
        reviewComment.save();
    }
}
