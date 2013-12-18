package models;


import org.junit.Test;
import utils.JodaDateUtil;

import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

public class ReviewCommentTest extends ModelTest<ReviewComment> {

    @Test
    public void findByThread() {
        /* Test comments in NonRangedCodeCommentThread */

        // given
        addTestData();
        CommentThread thread = CommentThread.find.byId(1l);
        assertThat(thread).isNotNull();

        // when
        List<ReviewComment> commentList = ReviewComment.findByThread(1l);

        // then
        assertThat(commentList.size()).isEqualTo(2);
        assertThat(commentList.get(0).id).isEqualTo(2);
        assertThat(commentList.get(1).id).isEqualTo(1);


        /* Test comments in CodeCommentThread */

        // given
        thread = CommentThread.find.byId(2l);
        assertThat(thread).isNotNull();

        // when
        commentList = ReviewComment.findByThread(2l);

        // then
        assertThat(commentList.size()).isEqualTo(1);
        assertThat(commentList.get(0).id).isEqualTo(3l);
    }

    /**
     * {@code commitId}를 가지는 {@link models.NonRangedCodeCommentThread} 한 개 저장.
     *  - state: OPEN
     *  - createDate: 3일전
     *  - id: 1
     * {@link models.ReviewComment} 생성하여 {@link models.NonRangedCodeCommentThread}에 추가.
     *  - createdDate: 3일전
     *  - id: 1
     * {@link models.ReviewComment} 생성하여 {@link models.NonRangedCodeCommentThread}에 추가.
     *  - createdDate: 2일전
     *  - id: 2
     * {@code commitId}를 가지는 {@link models.CodeCommentThread} 한 개 저장.
     *  - state: CLOSED
     *  - path: "readme.md"
     *  - createdDate: 2일전
     *  - id: 2
     * {@code }
     * {@link models.ReviewComment} 생성하여 {@link models.CodeCommentThread}에 추가.
     *  - createdDate: 3일전
     *  - id: 1
     */
    private void addTestData() {
        NonRangedCodeCommentThread thread1 = new NonRangedCodeCommentThread();
        thread1.commitId = "123456";
        thread1.state = CommentThread.ThreadState.OPEN;
        thread1.createdDate = JodaDateUtil.before(3);
        thread1.save();

        ReviewComment reviewComment1 = new ReviewComment();
        thread1.addComment(reviewComment1);
        reviewComment1.createdDate = JodaDateUtil.before(3);
        reviewComment1.setContents("reviewComment1");
        reviewComment1.save();

        ReviewComment reviewComment3 = new ReviewComment();
        thread1.addComment(reviewComment3);
        reviewComment3.createdDate = JodaDateUtil.before(2);
        reviewComment3.setContents("reviewComment3");
        reviewComment3.save();


        CodeCommentThread thread2 = new CodeCommentThread();
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

        ReviewComment reviewComment2 = new ReviewComment();
        reviewComment2.thread = thread2;
        reviewComment2.createdDate = JodaDateUtil.before(2);
        reviewComment2.setContents("reviewComment2");
        reviewComment2.save();
    }

}
