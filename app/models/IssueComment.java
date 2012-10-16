/**
 * @author Taehyun Park
 */

package models;

import org.joda.time.*;
import play.data.validation.*;
import play.db.ebean.*;
import utils.*;

import javax.persistence.*;
import java.util.*;

@Entity
public class IssueComment extends Model {
    private static final long serialVersionUID = 1L;
    private static Finder<Long, IssueComment> find = new Finder<Long, IssueComment>(Long.class,
            IssueComment.class);

    @Id
    public Long id;

    @Constraints.Required
    public String contents;

    public Date date;
    public Long authorId;
    public String authorName;
    public String filePath;

    @ManyToOne
    public Issue issue;

    public IssueComment() {
        date = JodaDateUtil.now();
    }

    public static IssueComment findById(Long id) {
        return find.byId(id);
    }

    public static Long create(IssueComment issueComment) {
        issueComment.save();
        return issueComment.id;
    }

    public String authorName() {
        return User.findNameById(this.authorId);
    }

    public static void delete(Long id) {
        find.byId(id).delete();
    }

    public static boolean isAuthor(Long currentUserId, Long id) {
        int findRowCount = find.where().eq("authorId", currentUserId).eq("id", id).findRowCount();
        return (findRowCount != 0) ? true : false;
    }

    public Duration ago() {
        return JodaDateUtil.ago(this.date);
    }
}
