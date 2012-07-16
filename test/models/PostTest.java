/**
 * @author Ahn Hyeok Jun
 */

package models;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.*;

import org.junit.*;

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

    @Test
    public void findWriter() throws Exception {
        // Given
        // When
        Post post = Post.findById(1l);
        String name = post.findWriter();
        // Then
        assertThat(name).isEqualTo("hobi");
    }

    @Test
    public void edit() throws Exception {
        // Given
        Post post = new Post();
        post.contents = "수정되었습니다.";
        post.id = 1l;
        // When
        Post.edit(post);
        // Then
        Post actual = Post.findById(1l);
        assertThat(actual.contents).isEqualTo("수정되었습니다.");
        assertThat(actual.commentCount).isEqualTo(1);
    }
}
