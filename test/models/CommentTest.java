package models;

import static org.fest.assertions.Assertions.assertThat;

import org.junit.*;

public class CommentTest extends ModelTest<Comment> {

    @Test
    public void findById() {
        // Given
        // When
        // Then
        assertThat(Comment.findById(1l).author.id).isEqualTo(1l);
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
        assertThat(Comment.findById(id)).isNotNull();
    }
}
