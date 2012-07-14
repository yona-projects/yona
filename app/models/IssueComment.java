package models;

import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.Id;

import play.data.validation.Constraints;
import play.db.ebean.Model;
import utils.JodaDateUtil;

@Entity
public class IssueComment extends Model {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    @Id
    public Long id;
    @Constraints.Required
    public Long issueId;
    @Constraints.Required
    public Long userId;
    @Constraints.Required
    public String contents;
    @Constraints.Required
    public Date date;
    public String filePath;
    

    public IssueComment() {
        date = JodaDateUtil.today();
    }

    public static Finder<Long, IssueComment> find = new Finder<Long, IssueComment>(
            Long.class, IssueComment.class);

    public static List<IssueComment> findCommentsByIssueId(Long issueId) {
        return find.where().eq("issueId", issueId).findList();
    }

    public static Long create(IssueComment issueComment) {
        Issue.countUpCommentCounter(issueComment.issueId);
        issueComment.save();
        return issueComment.id;
    }
    
    public static void deleteByIssueId(Long issueId) {
        List<IssueComment> targets = IssueComment.find.where().eq("issueId", "" + issueId).findList();

        Iterator<IssueComment> target = targets.iterator();
        while (target.hasNext()) {
            IssueComment issueComment = target.next();
            issueComment.delete();
        }
    }
}
