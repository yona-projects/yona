/**
 * @author Ahn Hyeok Jun
 */

package models;

import static org.fest.assertions.Assertions.assertThat;

import controllers.AbstractPostingApp;
import controllers.SearchApp;
import models.enumeration.Direction;

import models.enumeration.Matching;
import models.support.FinderTemplate;
import models.support.OrderParams;
import models.support.SearchParams;
import org.junit.*;

import utils.JodaDateUtil;

import com.avaje.ebean.Page;

import com.avaje.ebean.Ebean;

import java.util.List;

public class PostingTest extends ModelTest<Posting> {

    @Test
    public void findById() throws Exception {
        // Given
        // When
        Posting actual = Posting.finder.byId(1l);
        // Then
        assertThat(actual).isNotNull();
        assertThat(actual.title).isEqualTo("게시판이 새로 생성되었습니다.");
        assertThat(actual.authorId).isEqualTo(2l);
    }

    @Test
    public void findOnePage() throws Exception {
        // Given
        // When
        SearchParams searchParam = new SearchParams()
            .add("project.owner", "hobi", Matching.EQUALS)
            .add("project.name", "nForge4java", Matching.EQUALS)
            .add("body", "", Matching.CONTAINS);
        OrderParams orderParams = new OrderParams().add("id", Direction.DESC);
        Page<Posting> page = FinderTemplate.getPage(orderParams, searchParam, Posting.finder, AbstractPostingApp.ITEMS_PER_PAGE, 0);
        // Then
        assertThat(page.getList()).hasSize(1);
    }

    @Test
    public void save() throws Exception {
        // Given
        Posting post = new Posting();
        post.setBody("new Contents");
        post.title = "new_title";
        post.createdDate = JodaDateUtil.now();
        post.project = Project.find.byId(1l);
        post.setAuthor(getTestUser());

        // When
        post.save();

        // Then
        List<Posting> postings = Ebean.find(Posting.class).findList();
        Posting actual = Posting.finder.byId(post.id);
        assertThat(post.id).isGreaterThan(0);
        assertThat(actual.title).isEqualTo(post.title);
        assertThat(actual.getBody()).isEqualTo(post.getBody());
        assertThat(actual.createdDate).isEqualTo(post.createdDate);
        assertThat(actual.authorId).isEqualTo(getTestUser().id);
        assertThat(actual.id).isEqualTo(post.id);
    }

    @Test
    public void delete() throws Exception {
        // Given
        // When
        Posting.finder.byId(1l).delete();
        // Then
        assertThat(Posting.finder.byId(1l)).isNull();
        assertThat(PostingComment.find.byId(1l)).isNull();
    }

    @Test
    public void update() throws Exception {
        // Given
        Posting post = Posting.finder.byId(1l);
        post.setBody("수정되었습니다.");
        post.id = 1l;

        // When
        post.update();

        // Then
        Posting actual = Posting.finder.byId(1l);
        assertThat(actual.getBody()).isEqualTo("수정되었습니다.");
        assertThat(actual.numOfComments).isEqualTo(1);
    }

    @Test
    public void author() throws Exception {
        // Given
        Long currentUserId_hobi = 2l;
        Long postId = 1l;
        // When
        boolean result = Posting.finder.byId(postId).asResource().getAuthorId().equals(currentUserId_hobi);
        // Then
        assertThat(result).isEqualTo(true);
    }

	@Test
	public void findNonIssues() {
		// Given
		SearchApp.ContentSearchCondition condition = new SearchApp.ContentSearchCondition();
		condition.filter = "많은";
		condition.page = 1;
		condition.pageSize = 10;
		Project project = Project.find.byId(1l);

		// When
		Page<Posting> postPage = Posting.find(Posting.finder, project, condition);

		// Then
		assertThat(postPage.getList().size()).isEqualTo(1);
	}
}
