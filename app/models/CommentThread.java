package models;

import play.data.format.Formats;
import play.data.validation.Constraints;
import play.db.ebean.Model;

import javax.persistence.*;
import java.util.Date;
import java.util.List;

/**
 * @author Keesun Baik
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public class CommentThread extends Model {

    private static final long serialVersionUID = 1L;

    public static final Finder<Long, CommentThread> find = new Finder<>(Long.class, CommentThread.class);

    @Id
    public Long id;

    @Embedded
    public UserIdent author;

    @OneToMany(mappedBy = "thread")
    public List<ReviewComment> reviewComments;

    @Enumerated(EnumType.STRING)
    public ThreadState state;

    @Constraints.Required
    @Formats.DateTime(pattern = "YYYY/MM/DD/hh/mm/ss")
    public Date createdDate;

    public static List<CommentThread> findByCommitId(String commitId) {
        return find.where()
                .eq("commitId", commitId)
                .order().desc("createdDate")
                .findList();
    }

    public static List<CommentThread> findByCommitIdAndState(String commitId, ThreadState state) {
        return find.where()
                .eq("commitId", commitId)
                .eq("state", state)
                .order().desc("createdDate")
                .findList();
    }

    enum ThreadState {
        OPEN, CLOSED;
    }

}
