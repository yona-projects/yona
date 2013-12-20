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
        Project project = new Project();
        project.name = "testProject";
        project.save();

        CodeCommentThread codeCommentThread = new CodeCommentThread();
        codeCommentThread.createdDate = JodaDateUtil.now();
        codeCommentThread.commitId = "1234568";
        codeCommentThread.project = project;
        codeCommentThread.save();
        Long id = codeCommentThread.id;

        // when
        CodeCommentThread savedCodeCommentThread = CodeCommentThread.find.byId(id);

        // then
        assertThat(savedCodeCommentThread.prevCommitId).isEqualTo(StringUtils.EMPTY);
    }
}
