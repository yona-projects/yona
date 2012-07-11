package models;

import static org.fest.assertions.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;

public class CommentTest extends ModelTest {

    private User testUser;

    @Before
    public void setUp() {
        testUser = User.findByName("hobi");
    }

    @Test
    public void deleteByPostId() throws Exception {
        // Given
        // When
        Comment.deleteByPostId(1l);        
        // Then
        assertThat(Comment.findCommentsByPostId(1l)).isEmpty();
    }

    @Test
    public void write() throws Exception {
        // Given
        Comment comment = new Comment();
        comment.postId = 1l;
        comment.contents = "testThing";
        comment.userId = testUser.id;
        // When
        long id = Comment.write(comment);
        // Then
        assertThat(Comment.find.byId(id)).isNotNull();
    }
}
