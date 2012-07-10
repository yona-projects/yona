package models;

import play.data.format.Formats;
import play.data.validation.Constraints;
import play.db.ebean.Model;

import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.Date;
import java.util.List;


/**
 * Milestone entity managed by Ebean
 */
@Entity
public class Milestone extends Model {

    public static Finder<Long, Milestone> find = new Finder<Long, Milestone>(Long.class, Milestone.class);

    @Id
    public Long id;

    @Constraints.Required
    public String versionName;

    @Constraints.Required
    @Formats.DateTime(pattern = "yyyy-MM-dd")
    public Date dueDate;

    @Constraints.Required
    public String contents;

    public int numClosedIssues;

    public int numOpenIssues;

    // 추후 추가해야할 듯
    //public int projectId; //foreign key

    public static List<Milestone> findOnePage(int pageNum) {
        return find.findPagingList(10).getPage(pageNum - 1).getList();
    }

    public static void create(Milestone milestone) {
        milestone.save();
    }

    public static void delete(Long id) {
        find.ref(id).delete();
    }

    public static Milestone findById(Long id) {
        return find.ref(id);
    }
}
