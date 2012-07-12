/**
 * @author Ahn Hyeok Jun
 */

package models;

import java.util.*;

import javax.persistence.*;

import play.data.format.Formats;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import utils.JodaDateUtil;

import com.avaje.ebean.Page;

@Entity
public class Post extends Model {
    private static final long serialVersionUID = 1L;

    @Id
    public Long id;
    public Long userId;
    @Constraints.Required
    public String title;
    @Constraints.Required
    public String contents;
    @Constraints.Required
    @Formats.DateTime(pattern = "YYYY/MM/DD/hh/mm/ss")
    public Date date;
    public int commentCount;
    public String filePath;

    public Post() {
        this.date = JodaDateUtil.today();
        this.commentCount = 0;
    }

    private static Finder<Long, Post> find = new Finder<Long, Post>(Long.class, Post.class);

    public static Post findById(Long id) {
        return find.byId(id);
    }

    public static Page<Post> findOnePage(int pageNum) {
        return find.orderBy("id desc").findPagingList(10).getPage(pageNum - 1);
    }

    public static Long write(Post post) {
        post.save();
        return post.id;
    }

    public static void delete(Long id) {
        find.byId(id).delete();
        Comment.deleteByPostId(id);
    }

    public static void countUpCommentCounter(Long id) {
        Post post = findById(id);
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

    public String findWriter() {
        return User.findNameById(this.userId);
    }
}
