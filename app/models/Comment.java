package models;

import play.data.validation.Constraints;
import play.db.ebean.Model;
import utils.JodaDateUtil;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import org.joda.time.Duration;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Entity
public class Comment extends Model {
    private static final long serialVersionUID = 1L;
    private static Finder<Long, Comment> find = new Finder<Long, Comment>(Long.class, Comment.class);

    @Id
    public Long id;

    @Constraints.Required
    public String contents;

    @Constraints.Required
    public Date date;

    public String filePath;
    public Long authorId;
    @ManyToOne
    public Post post;

    public Comment() {
        date = JodaDateUtil.now();
    }

    public static Comment findById(Long id) {
        return find.byId(id);
    }

    public static Long write(Comment comment) {
        comment.save();
        return comment.id;
    }

    public static List<Comment> findCommentsByPostId(Long postId) {
        return find.where().eq("post.id", postId).findList();
    }

    public String calcPassTime() {
        Duration dur = JodaDateUtil.ago(this.date);
        if (dur.getStandardDays() > 0) {
            return dur.getStandardDays() + "일 전";
        } else if (dur.getStandardHours() > 0) {
            return dur.getStandardHours() + "시간 전";
        } else if (dur.getStandardMinutes() > 0) {
            return dur.getStandardMinutes() + "분 전";
        } else if (dur.getStandardSeconds() > 0) {
            return dur.getStandardSeconds() + "초 전";
        } else {
            return "방금 전";
        }
    }

    public String authorName() {
        return User.findNameById(this.authorId);
    }
}
