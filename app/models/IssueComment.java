/**
 * @author Taehyun Park
 */

package models;

import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import models.enumeration.PermissionOperation;
import models.enumeration.PermissionResource;

import play.data.validation.Constraints;
import play.db.ebean.Model;
import utils.JodaDateUtil;
import utils.RoleCheck;

@Entity
public class IssueComment extends Model {
    private static final long serialVersionUID = 1L;
    private static Finder<Long, IssueComment> find = new Finder<Long, IssueComment>(Long.class,
            IssueComment.class);

    @Id
    public Long id;

    @Constraints.Required
    public String contents;

    @Constraints.Required
    public Date date;

    public Long authorId;
    public String filePath;

    @ManyToOne
    public Issue issue;

    public IssueComment() {
        date = JodaDateUtil.today();
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

    public boolean isAuthor(Long currentUserId, Long objectId, String projectName) {

        Project project = Project.findByName(projectName);
        boolean authorIs;
        if (currentUserId == findById(objectId).authorId
                || RoleCheck.roleCheck(currentUserId, project.id,
                        PermissionResource.PROJECT.resource(),
                        PermissionOperation.WRITE.operation())) {
            authorIs = true;
        } else {
            authorIs = false;
        }

        return authorIs;

    }

}
