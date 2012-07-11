/**
 * @author Ahn Hyeok Jun
 */

package models;

import org.junit.*;

import play.test.*;
import static play.test.Helpers.*;
import static org.fest.assertions.Assertions.*;
import static org.junit.Assert.*;

public class PostTest extends ModelTest {

    private User testUser;

    @Before
    public void setUp() {
        testUser = User.findByName("hobi");
    }

    @Test
    public void findById() throws Exception {
        // Given
        // When
        Post actual = Post.findById(1l);
        // Then
        assertThat(actual).isNotNull();
        assertThat(actual.title).isEqualTo("게시판이 새로 생성되었습니다.");
        assertThat(actual.userId).isEqualTo(1L);
    }

    @Test
    public void write() throws Exception {
        // Given
        Post post = new Post();
        post.contents = "new Contents";
        post.title = "new_title";
        post.userId = testUser.id;
        // When
        Long id = Post.write(post);
        // Then
        Post actual = Post.findById(id);

        assertThat(actual.title).isEqualTo(post.title);
        assertThat(actual.contents).isEqualTo(post.contents);
        assertThat(actual.date).isEqualTo(post.date);
        assertThat(actual.userId).isEqualTo(1l);
        assertThat(actual.id).isEqualTo(id);
    }

    @Test
    public void delete() throws Exception {
        // Given
        // When
        Post.delete(1l);
        // Then
        assertThat(Post.findById(1l)).isNull();
    }
}
