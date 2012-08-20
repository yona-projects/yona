/**
 * @author Ahn Hyeok Jun
 */

package models;

import java.util.*;

import javax.persistence.*;

import controllers.SearchApp;
import models.enumeration.*;
import models.support.*;

import org.joda.time.Duration;

import play.data.format.Formats;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import utils.JodaDateUtil;

import com.avaje.ebean.Page;
import static com.avaje.ebean.Expr.contains;

@Entity
public class Post extends Model {
    private static final long serialVersionUID = 1L;
    private static Finder<Long, Post> find = new Finder<Long, Post>(Long.class, Post.class);

    @Id
    public Long id;

    @Constraints.Required
    public String title;

    @Constraints.Required
    public String contents;

    @Constraints.Required
    @Formats.DateTime(pattern = "YYYY/MM/DD/hh/mm/ss")
    public Date date;

    public int commentCount;
    public String filePath;

    public Long authorId;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL)
    public List<Comment> comments;

    @ManyToOne
    public Project project;

    public Post() {
        this.date = JodaDateUtil.now();
    }

    public static Post findById(Long id) {
        return find.byId(id);
    }

    /**
     * @param projectName
     *            프로젝트이름
     * @param pageNum
     *            페이지 번호
     * @param direction
     *            오름차순(asc), 내림차순(decs)
     * @param key
     *            오름차순과 내림차수를 결정하는 기준
     * @return
     */
    public static Page<Post> findOnePage(String ownerName, String projectName, int pageNum,
            Direction direction, String key) {
        SearchParams searchParam = new SearchParams()
            .add("project.owner", ownerName, Matching.EQUALS)
            .add("project.name", projectName, Matching.EQUALS);
        OrderParams orderParams = new OrderParams().add(key, direction);
        return FinderTemplate.getPage(orderParams, searchParam, find, 10, pageNum - 1);
    }

    public static Long write(Post post) {
        post.save();
        return post.id;
    }

    public static void delete(Long id) {
        find.byId(id).delete();
    }

    /**
     * 댓글이 달릴때 체크를 하는 함수.
     * @param id Post의 ID
     */
    public static void countUpCommentCounter(Long id) {
        Post post = find.byId(id);
        post.commentCount++;
        post.update();
    }

    /**
     * 현재 글을 쓴지 얼마나 되었는지를 얻어내는 함수
     * @return
     */
    public Duration ago() {
        return JodaDateUtil.ago(this.date);
    }

    public static void edit(Post post) {
        Post beforePost = findById(post.id);
        post.commentCount = beforePost.commentCount;
        if (post.filePath == null) {
            post.filePath = beforePost.filePath;
        }
        post.update();
    }
    
    public static boolean isAuthor(Long currentUserId, Long id) {
        int findRowCount = find.where().eq("authorId", currentUserId).eq("id", id).findRowCount();
        return (findRowCount != 0) ? true : false;
    }

    public String authorName() {
        return User.findNameById(this.authorId);
    }

	/**
	 * 전체 컨텐츠 검색할 때 제목과 내용에 condition.filter를 포함하고 있는 게시글를 검색한다.
	 * @param project
	 * @param condition
	 * @return
	 */
	public static Page<Post> findPosts(Project project, SearchApp.ContentSearchCondition condition) {
		String filter = condition.filter;
		return find.where()
				.eq("project.id", project.id)
				.or(contains("title", filter), contains("contents", filter))
				.findPagingList(condition.pageSize)
				.getPage(condition.page - 1);
	}
}
