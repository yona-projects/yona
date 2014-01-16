/*
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
import java.util.Map;
import java.util.List;
import java.util.Date;
import java.util.HashMap;
import play.test.Helpers;
import utils.JodaDateUtil;
import controllers.ProjectApp;
import static org.fest.assertions.Assertions.assertThat;


/**
 * {@link models.support.ReviewSearchCondition}을 테스트
 */
public class ReviewSearchConditionTest extends ModelTest<ReviewSearchCondition> {
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
        searchCondition.authorId = User.findByLoginId("admin").id;
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
        HashMap<String, Object> properties = new HashMap<>();
        CommentThread thread;


        properties.put("createdDate", JodaDateUtil.before(1));
        properties.put("commitId", "controllers");
        properties.put("prevCommitId", "99");
        properties.put("state", CommentThread.ThreadState.OPEN);
        thread = makeThread(CodeCommentThread.class, adminUser, "Comment #1 : 111", project, properties);

        properties.clear();
        makeComment(thread, lazielUser, "Comment #1-1", properties);
        makeComment(thread, lazielUser, "Comment #1-2", properties);


        properties.clear();
        properties.put("createdDate", JodaDateUtil.before(2));
        properties.put("commitId", "200");
        properties.put("prevCommitId", "199");
        properties.put("state", CommentThread.ThreadState.OPEN);
        thread = makeThread(CodeCommentThread.class, adminUser, "Comment #2 : /app/controllers/BoardApp.java", project, properties);

        properties.clear();
        makeComment(thread, adminUser, "Comment #2-1", properties);
        makeComment(thread, lazielUser, "Comment #2-2", properties);


        properties.clear();
        properties.put("createdDate", JodaDateUtil.before(3));
        properties.put("commitId", "300");
        properties.put("prevCommitId", "299");
        properties.put("state", CommentThread.ThreadState.OPEN);
        properties.put("path", "/app/controllers/IssueApp.java");
        thread = makeThread(CodeCommentThread.class, lazielUser, "Comment #3", project, properties);

        properties.clear();
        makeComment(thread, lazielUser, "Comment #3-1", properties);
        makeComment(thread, adminUser, "Comment #3-2", properties);
        makeComment(thread, lazielUser, "Comment #3-3", properties);
    }

    @Before
    public void before() {
        Map<String, String> config = support.Config.makeTestConfig();
        app = Helpers.fakeApplication(config);
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
     * @param properties
     * @return
     */
    private ReviewComment makeComment(CommentThread thread, User author, String contents, HashMap<String, Object> properties) {
        if (!properties.containsKey("createdDate")) properties.put("createdDate", JodaDateUtil.now());

        ReviewComment reviewComment = new ReviewComment();
        reviewComment.createdDate = (Date)properties.get("createdDate");
        reviewComment.author = new UserIdent(author);
        reviewComment.thread = thread;
        reviewComment.setContents(contents);
        reviewComment.save();

        return reviewComment;
    }

    /**
     * 스레드 생성 함수.
     * @param type
     * @param author
     * @param contents
     * @param project
     * @param properties
     * @return
     */
    private CommentThread makeThread(Class type, User author, String contents, Project project, HashMap<String, Object> properties) {
        CommentThread thread;

        if (!properties.containsKey("createdDate")) properties.put("createdDate", JodaDateUtil.now());
        if (!properties.containsKey("commitId")) properties.put("commitId", Long.toHexString(Double.doubleToLongBits(Math.random())));
        if (!properties.containsKey("prevCommitId")) properties.put("prevCommitId", Long.toHexString(Double.doubleToLongBits(Math.random())));
        if (!properties.containsKey("state")) properties.put("state", (Math.random() % 2 == 0) ? CommentThread.ThreadState.OPEN : CommentThread.ThreadState.CLOSED);
        if (!properties.containsKey("path")) properties.put("path", "/README.md");

        if (type == CodeCommentThread.class) {
            CodeCommentThread codeCommentThread = new CodeCommentThread();

            codeCommentThread.commitId = properties.get("commitId").toString();
            codeCommentThread.prevCommitId = properties.get("prevCommitId").toString();
            CodeRange codeRange = new CodeRange();
            codeRange.path = properties.get("path").toString();
            codeCommentThread.codeRange = codeRange;

            thread = codeCommentThread;
        } else if (type == NonRangedCodeCommentThread.class) {
            NonRangedCodeCommentThread nonRangedCodeCommentThread = new NonRangedCodeCommentThread();

            nonRangedCodeCommentThread.commitId = properties.get("commitId").toString();

            thread = nonRangedCodeCommentThread;
        } else {
            return null;
        }

        thread.createdDate = (Date)properties.get("createdDate");
        thread.state = (CommentThread.ThreadState)properties.get("state");
        thread.project = project;
        thread.author = new UserIdent(author);
        thread.save();


        ReviewComment reviewComment = new ReviewComment();
        reviewComment.createdDate = thread.createdDate;
        reviewComment.author = thread.author;
        reviewComment.thread = thread;
        reviewComment.setContents(contents);
        reviewComment.save();

        return thread;
    }
}
