package models;

import play.data.validation.Constraints;
import play.db.ebean.Model;

import javax.persistence.*;
import java.util.Date;
import java.util.List;

/**
 * @author Keesun Baik
 */
@Entity
public class ReviewComment extends Model {
    public static final Finder<Long, ReviewComment> find = new Finder<>(Long.class, ReviewComment.class);

    @Id
    public Long id;

    @Lob
    @Constraints.Required
    private String contents;

    @Constraints.Required
    public Date createdDate;

    @Embedded
    public UserIdent author;

    @ManyToOne
    public CommentThread thread;

    public void setContents(String contents) {
        this.contents = contents;
    }

    public String getContents() {
        return contents;
    }

    public static List<ReviewComment> findByThread(Long threadId) {
        return find.where()
                .eq("thread.id", threadId)
                .order().desc("createdDate")
                .findList();
    }
}
