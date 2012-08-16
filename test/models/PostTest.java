/**
 * @author Ahn Hyeok Jun
 */

package models;

import static org.fest.assertions.Assertions.assertThat;

import models.enumeration.Direction;

import org.junit.*;

import com.avaje.ebean.Page;

import controllers.BoardApp;

public class PostTest extends ModelTest<Post> {

    @Test
    public void findById() throws Exception {
        // Given
        // When
        Post actual = Post.findById(1l);
        // Then
        assertThat(actual).isNotNull();
        assertThat(actual.title).isEqualTo("게시판이 새로 생성되었습니다.");
        assertThat(actual.authorId).isEqualTo(2l);
    }

    @Test
    public void findOnePage() throws Exception {
        // Given
        // When
        Page<Post> page = Post.findOnePage("hobi", "nForge4java", 1, Direction.DESC,
                BoardApp.SearchCondition.ORDERING_KEY_ID);
        // Then
        assertThat(page.getList()).hasSize(1);
    }

    @Test
    public void write() throws Exception {
        // Given
        Post post = new Post();
        post.contents = "new Contents";
        post.title = "new_title";
        post.authorId = getTestUser().id;
        // When
        Long id = Post.write(post);
        // Then
        Post actual = Post.findById(id);

        assertThat(actual.title).isEqualTo(post.title);
        assertThat(actual.contents).isEqualTo(post.contents);
        assertThat(actual.date).isEqualTo(post.date);
        assertThat(actual.authorId).isEqualTo(getTestUser().id);
        assertThat(actual.id).isEqualTo(id);
    }

    @Test
    public void delete() throws Exception {
        // Given
        // When
        Post.delete(1l);
        flush();
        // Then
        assertThat(Post.findById(1l)).isNull();
        assertThat(Comment.findById(1l)).isNull();
    }

    @Test
    public void edit() throws Exception {
        // Given
        Post post = new Post();
        post.contents = "수정되었습니다.";
        post.id = 1l;
        // When
        Post.edit(post);
        flush();
        // Then
        Post actual = Post.findById(1l);
        assertThat(actual.contents).isEqualTo("수정되었습니다.");
        assertThat(actual.commentCount).isEqualTo(1);
    }
    
    @Test
    public void isAuthor() throws Exception {
        // Given
        Long currentUserId_hobi = 2l;
        Long postId = 1l;
        // When
        boolean result = Post.isAuthor(currentUserId_hobi, postId);
        // Then
        assertThat(result).isEqualTo(true);
    }
}
