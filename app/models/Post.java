/**
 * @author Ahn Hyeok Jun
 */

package models;

import com.avaje.ebean.Page;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import utils.JodaDateUtil;
import views.html.board.post;

import javax.persistence.*;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;

@Entity
public class Post extends Model {
    private static final long serialVersionUID = 1L;
    private static Finder<Long, Post> find = new Finder<Long, Post>(Long.class, Post.class);

    public final static String ORDER_ASCENDING = "asc";
    public final static String ORDER_DESCENDING = "desc";

    public final static String ORDERING_KEY_ID = "id";
    public final static String ORDERING_KEY_TITLE = "title";
    public final static String ORDERING_KEY_AGE = "date";

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
    
    @ManyToOne
    public User author;
    
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL)
    public Set<Comment> comments;
    
    @ManyToOne
    public Project project;

    public Post() {
        this.date = JodaDateUtil.today();
    }

    public static Post findById(Long id) {
        return find.byId(id);
    }

    /**
     * ! FIXME untested
     * @param projectName
     * @param pageNum
     * @param order
     * @param key
     * @return
     */
    public static Page<Post> findOnePage(String projectName, int pageNum, String order, String key) {
        return find.where()
                .eq("project.name", projectName)
                .orderBy(key + " " +order)
                .findPagingList(10)
                .getPage(pageNum - 1);
    }
    
    /**
     * ! FIXME unused, untested
     * @param pageNum 페이지 번호
     * @param order   오름차순(asc), 내림차순(decs)
     * @param key     오름차순과 내림차수를 결정하는 기준
     * @param filter  검색어
     * @return 
     */
    public static Page<Post> findOnePage(int pageNum, String order, String key, String filter) {
        return find.where()
                .ilike("title", filter)
                .orderBy(key + " " + order)
                .findPagingList(10)
                .getPage(pageNum - 1);
    }

    public static Long write(Post post) {
        post.save();
        return post.id;
    }

    public static void delete(Long id) {
        find.byId(id).delete();
        Comment.deleteByPostId(id);
    }

    public static void countUpCommentCounter(Post post) {
        post.commentCount++;
        post.update();
    }

    public String calcPassTime() {
        // TODO 경계값 검사하면 망할함수. 나중에 라이브러리 쓸예정
        Calendar today = Calendar.getInstance();

        long dTimeMili = today.getTime().getTime() - this.date.getTime();

        Calendar dTime = Calendar.getInstance();
        dTime.setTimeInMillis(dTimeMili);

        if (dTimeMili < 60 * 1000) {
            return "방금 전";
        } else if (dTimeMili < 60 * 1000 * 60) {
            return dTime.get(Calendar.MINUTE) + "분 전";
        } else if (dTimeMili < 60 * 1000 * 60 * 24) {
            return dTime.get(Calendar.HOUR) + "시간 전";
        } else if (dTimeMili < 60 * 1000 * 60 * 24 * 30) {
            return dTime.get(Calendar.DATE) + "일 전";
        } else if (dTimeMili < 60 * 1000 * 60 * 24 * 30 * 12) {
            return dTime.get(Calendar.MONDAY) + "달 전";
        } else {
            return dTime.get(Calendar.YEAR) + "년 전";
        }
    }

    public static void edit(Post post) {
        Post beforePost = findById(post.id);
        post.commentCount = beforePost.commentCount;
        if (post.filePath == null) {
            post.filePath = beforePost.filePath;
        }
        post.update();
    }
}
