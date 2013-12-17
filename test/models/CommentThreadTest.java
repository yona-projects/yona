package models;

import org.junit.Test;

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

}
