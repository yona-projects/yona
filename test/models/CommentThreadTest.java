package models;

import org.junit.Test;
import utils.JodaDateUtil;

import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author Keesun Baik
 */
public class CommentThreadTest extends ModelTest<CommentThread>  {

    @Test
    public void save() {
        SimpleCommentThread thread = new SimpleCommentThread();
        thread.state = CommentThread.ThreadState.OPEN;
        thread.save();
    }

    @Test
    public void findByCommitId() {
        // given
        String commitId = "123123";
        addTestData(commitId);

        // when
        List<CommentThread> threadList = CommentThread.findByCommitId(commitId);

        // then
        assertThat(threadList.size()).isEqualTo(2);
        assertThat(threadList.get(0).createdDate).isEqualTo(JodaDateUtil.before(2));
        assertThat(threadList.get(1).createdDate).isEqualTo(JodaDateUtil.before(3));
    }

    /**
     * {@code commitId}를 가지는 {@link models.NonRangedCodeCommentThread} 한 개 저장.
     *  - state: OPEN
     *  - createDate: 3일전
     * {@code commitId}를 가지는 {@link models.CodeCommentThread} 한 개 저장.
     *  - state: CLOSED
     *  - path: "readme.md"
     *  - createdDate: 2일전
     * 123321을 커밋 ID로 가지는 {@link models.NonRangedCodeCommentThread} 한 개 저장.
     *  - state: OPEN
     *  - createdDate: 1일전
     *
     * @param commitId
     */
    private void addTestData(String commitId) {
        NonRangedCodeCommentThread thread1 = new NonRangedCodeCommentThread();
        thread1.commitId = commitId;
        thread1.state = CommentThread.ThreadState.OPEN;
        thread1.createdDate = JodaDateUtil.before(3);
        thread1.save();

        CodeCommentThread thread2 = new CodeCommentThread();
        thread2.commitId = commitId;
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

        NonRangedCodeCommentThread thread3 = new NonRangedCodeCommentThread();
        thread3.commitId = "123321";
        thread3.state = CommentThread.ThreadState.OPEN;
        thread3.createdDate = JodaDateUtil.before(1);
        thread3.save();
    }


}
