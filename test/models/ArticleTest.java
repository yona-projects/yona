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
        Article article = new Article();
        article.contents = "new Contents";
        article.title = "new_title";
        article.writerId = testUser.id;
        int id = Article.write(article);

        Article actual = Article.findById(id);
        assertThat(actual).isNotNull();
    }

    @Test
    public void testWrite() throws Exception {

        Article article = new Article();
        article.contents = "new Contents";
        article.title = "new_title";
        article.writerId = testUser.id;
        int id = Article.write(article);

        Article actual = Article.findById(id);

        assertThat(actual.title).isEqualTo(article.title);
        assertThat(actual.contents).isEqualTo(article.contents);
        assertThat(actual.date).isEqualTo(article.date);
        assertThat(actual.writerId).isEqualTo(1l);
        assertThat(actual.articleNum).isEqualTo(id);
    }

    @Test
    public void testDelete() throws Exception {

        Article article = new Article();
        article.contents = "new Contents";
        article.title = "new_title";
        article.writerId = testUser.id;
        int id = Article.write(article);

        assertThat(Article.findById(id)).isNotNull();
        Article.delete(id);
        assertThat(Article.findById(id)).isEqualTo(null);
    }
}
