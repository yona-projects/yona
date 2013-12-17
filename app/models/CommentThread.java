package models;

import play.db.ebean.Model;

import javax.persistence.*;
import java.util.List;

/**
 * @author Keesun Baik
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public class CommentThread extends Model {

    private static final long serialVersionUID = 1L;

    @Id
    public Long id;

    @Embedded
    public UserIdent author;

    @OneToMany(mappedBy = "thread")
    public List<ReviewComment> reviewComments;

    @Enumerated(EnumType.STRING)
    public ThreadState state;

    enum ThreadState {
        OPEN, CLOSED;
    }

}
