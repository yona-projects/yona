package models;

import static org.fest.assertions.Assertions.assertThat;

import org.junit.*;

public class CommentTest extends ModelTest {

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
        comment.post = Post.findById(1l);
        comment.contents = "testThing";
        comment.author = getTestUser();
        // When
        long id = Comment.write(comment);
        // Then
        assertThat(Comment.find.byId(id)).isNotNull();
    }

    @Test
    @Ignore("The findWriter method was deleted.")
    public void findWriter() throws Exception {
        // Given
        // When
        String name = Comment.find.byId(1l).author.name;
//        Then
        assertThat(name).isEqualTo("hobi");
    }
}
