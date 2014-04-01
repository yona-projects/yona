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
 * {@link models.support.ReviewSearchCondition}을 테스트
 */
public class ReviewSearchConditionTest extends ModelTest<ReviewSearchCondition> {

    protected static FakeApplication app;

    /**
     * 리뷰에서 커밋 로그 검색을 테스트
     */
    @Test
    public void filterForComment() {
        // given
        Project project = ProjectApp.getProject("yobi", "projectYobi");
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
     * 리뷰에서 커밋 아이디 검색을 테스트
     */
    @Test
    public void filterForCommitId() {
        // given
        String testCommitId = "300";
        Project project = ProjectApp.getProject("yobi", "projectYobi");
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
     * 리뷰에서 파일 패스 검색을 테스트
     */
    @Test
    public void filterForPath() {
        // given
        String testPath = "/app/controllers/IssueApp.java";
        Project project = ProjectApp.getProject("yobi", "projectYobi");
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
     * 리뷰에서 커밋로그, 커밋아이디, 파일 path 검색을 테스트
     */
    @Test
    public void filterForAll() {
        // given
        String testFilter = "controllers";
        Project project = ProjectApp.getProject("yobi", "projectYobi");
        ReviewSearchCondition searchCondition = new ReviewSearchCondition();
        searchCondition.filter = testFilter;
        searchCondition.state = CommentThread.ThreadState.OPEN.name();

        // when
        List<CommentThread> expressionList = searchCondition.asExpressionList(project).findList();

        // then
        assertThat(expressionList.size()).isEqualTo(3);
    }

    /**
     * 스레드 생성자 검색을 테스트
     */
    @Test
    public void searchingAuthor() {
        // given
        Project project = ProjectApp.getProject("yobi", "projectYobi");
        ReviewSearchCondition searchCondition = new ReviewSearchCondition();
        searchCondition.authorId = User.findByLoginId("admin").id;
        searchCondition.state = CommentThread.ThreadState.OPEN.name();

        // when
        List<CommentThread> expressionList = searchCondition.asExpressionList(project).findList();

        // then
        assertThat(expressionList.size()).isEqualTo(2);
    }

    /**
     * 스레드 참여자 검색을 테스트
     */
    @Test
    public void searchingParticipant() {
        // given
        Project project = ProjectApp.getProject("yobi", "projectYobi");
        ReviewSearchCondition searchCondition = new ReviewSearchCondition();
        searchCondition.participationId = User.findByLoginId("admin").id;
        searchCondition.state = CommentThread.ThreadState.OPEN.name();

        // when
        List<CommentThread> expressionList = searchCondition.asExpressionList(project).findList();

        // then
        assertThat(expressionList.size()).isEqualTo(2);
    }

    /**
     * 테스트를 위해 DB에 데이터 입력
     */
    private void addTestData() {
        User adminUser = User.findByLoginId("admin");
        User lazielUser = User.findByLoginId("laziel");
        Project project = ProjectApp.getProject("yobi", "projectYobi");

        addTestThread1(adminUser, lazielUser, project);
        addTestThread2(adminUser, lazielUser, project);
        addTestThread3(adminUser, lazielUser, project);
    }

    private void addTestThread1(User admin, User user, Project project) {
        CodeCommentThread thread = new CodeCommentThread();
        thread.createdDate = JodaDateUtil.today();
        thread.commitId = "controllers";
        thread.state = CommentThread.ThreadState.OPEN;
        makeThread(admin, "Comment #1 : 111", project, thread);
        makeComment(thread, user, "Comment #1-1");
        makeComment(thread, user, "Comment #1-2");
    }

    private void addTestThread2(User admin, User user, Project project) {
        CodeCommentThread thread = new CodeCommentThread();
        thread.createdDate = JodaDateUtil.before(2);
        thread.commitId = "200";
        thread.state = CommentThread.ThreadState.OPEN;
        makeThread(admin, "Comment #2 : /app/controllers/BoardApp.java", project, thread);
        makeComment(thread, admin, "Comment #2-1");
        makeComment(thread, user, "Comment #2-2");
    }

    private void addTestThread3(User admin, User user, Project project) {
        CodeCommentThread thread = new CodeCommentThread();
        thread.createdDate = JodaDateUtil.before(3);
        thread.commitId = "300";
        thread.state = CommentThread.ThreadState.OPEN;
        thread.codeRange.path = "/app/controllers/IssueApp.java";
        makeThread(user, "Comment #3", project, thread);
        makeComment(thread, user, "Comment #3-1");
        makeComment(thread, user, "Comment #3-2");
        makeComment(thread, user, "Comment #3-3");
    }


    @Before
    public void before() {
        app = support.Helpers.makeTestApplication();
        Helpers.start(app);
        addTestData();
    }

    @After
    public void after() {
        Helpers.stop(app);
    }

    /**
     * 커맨트 생성 함수. 지정한 스레드에 캐맨트 남긴다.
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
     * 스레드 생성 함수.
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
