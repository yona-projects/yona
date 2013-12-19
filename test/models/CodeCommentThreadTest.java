package models;

import org.junit.Test;
import utils.JodaDateUtil;
import org.apache.commons.lang.StringUtils;
import static org.fest.assertions.Assertions.assertThat;


/**
 * @author Changsung Kim
 */
public class CodeCommentThreadTest extends ModelTest<CodeCommentThread>  {
    @Test
    public void checkDefaultValueForPrevCommitId() {
        // given
        String commitId = "1234568";

        CodeCommentThread codeCommentThread = new CodeCommentThread();
        codeCommentThread.createdDate = JodaDateUtil.before(2);
        codeCommentThread.commitId = commitId;
        codeCommentThread.save();

        // when
        CodeCommentThread savedCodeCommentThread = CodeCommentThread.find.byId(1l);

        // then
        assertThat(savedCodeCommentThread.prevCommitId).isEqualTo(StringUtils.EMPTY);
    }
}
