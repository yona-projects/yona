/**
 * @author Ahn Hyeok Jun
 */

package models;

import com.avaje.ebean.*;
import controllers.*;
import models.enumeration.*;
import models.resource.Resource;
import models.support.*;
import org.joda.time.*;
import play.data.format.*;
import play.data.validation.*;
import play.db.ebean.*;
import utils.*;

import javax.persistence.*;
import javax.validation.constraints.Size;
import java.util.*;

import static com.avaje.ebean.Expr.*;
import static play.data.validation.Constraints.*;

@Entity
public class Post extends Model {
    private static final long serialVersionUID = 1L;
    private static Finder<Long, Post> finder = new Finder<Long, Post>(Long.class, Post.class);

    @Id
    public Long id;

    @Required @Size(max=255)
    public String title;

    @Required @Lob
    public String contents;

    @Required
    @Formats.DateTime(pattern = "YYYY/MM/DD/hh/mm/ss")
    public Date date;

    public int commentCount;
    public String filePath;

    public Long authorId;
    public String authorLoginId;
    public String authorName;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL)
    public List<Comment> comments;

    @ManyToOne
    public Project project;

    public Post() {
        this.date = JodaDateUtil.now();
    }

    public static Post findById(Long id) {
        return finder.byId(id);
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
            Direction direction, String key, String filter) {
        SearchParams searchParam = new SearchParams()
            .add("project.owner", ownerName, Matching.EQUALS)
            .add("project.name", projectName, Matching.EQUALS)
            .add("contents", filter, Matching.CONTAINS);
        OrderParams orderParams = new OrderParams().add(key, direction);
        return FinderTemplate.getPage(orderParams, searchParam, finder, 10, pageNum);
    }

    public static Long write(Post post) {
        post.save();
        return post.id;
    }

    public static void delete(Long id) {
        finder.byId(id).delete();
    }

    /**
     * 댓글이 달릴때 체크를 하는 함수.
     * @param id Post의 ID
     */
    public static void countUpCommentCounter(Long id) {
        Post post = finder.byId(id);
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
        int findRowCount = finder.where().eq("authorId", currentUserId).eq("id", id).findRowCount();
        return (findRowCount != 0) ? true : false;
    }

	/**
	 * 전체 컨텐츠 검색할 때 제목과 내용에 condition.filter를 포함하고 있는 게시글를 검색한다.
	 * @param project
	 * @param condition
	 * @return
	 */
	public static Page<Post> find(Project project, SearchApp.ContentSearchCondition condition) {
		String filter = condition.filter;
		return finder.where()
				.eq("project.id", project.id)
				.or(contains("title", filter), contains("contents", filter))
				.findPagingList(condition.pageSize)
				.getPage(condition.page - 1);
	}

    public static void countDownCommentCounter(Long id) {
        Post post = finder.byId(id);
        post.commentCount--;
        post.update();
    }

    public Resource asResource() {
        return new Resource() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public Project getProject() {
                return project;
            }

            @Override
            public ResourceType getType() {
                return ResourceType.BOARD_POST;
            }
        };
    }
}
