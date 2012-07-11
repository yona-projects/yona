/**
 * @author Ahn Hyeok Jun
 */

package models;

import org.junit.*;

import play.test.*;
import static play.test.Helpers.*;
import static org.fest.assertions.Assertions.*;
import static org.junit.Assert.*;

public class ArticleTest extends ModelTest {

    private User testUser;

    @Before
    public void setUp() {
        testUser = User.findByName("hobi");
    }

    @Test
    public void testFindById() throws Exception {
        // Given
        // When
        Article actual = Article.findById(1l);
        // Then
        assertThat(actual).isNotNull();
        assertThat(actual.title).isEqualTo("게시판이 새로 생성되었습니다.");
        assertThat(actual.writerId).isEqualTo(1L);
    }

    @Test
    public void testWrite() throws Exception {
        //Given
        Article article = new Article();
        article.contents = "new Contents";
        article.title = "new_title";
        article.writerId = testUser.id;
        //When
        Long id = Article.write(article);
        //Then
        Article actual = Article.findById(id);

        assertThat(actual.title).isEqualTo(article.title);
        assertThat(actual.contents).isEqualTo(article.contents);
        assertThat(actual.date).isEqualTo(article.date);
        assertThat(actual.writerId).isEqualTo(1l);
        assertThat(actual.articleNum).isEqualTo(id);
    }

    @Test
    public void testDelete() throws Exception {
        //Given
        //When
        Article.delete(1l);
        //Then
        assertThat(Article.findById(1l)).isNull();
    }
}
