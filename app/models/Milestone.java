package models;

import models.enumeration.Matching;
import models.enumeration.MilestoneState;
import models.enumeration.Ordering;
import models.support.FinderTemplate;
import models.support.OrderParams;
import models.support.SearchParams;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.db.ebean.Model;

import javax.persistence.Entity;
import javax.persistence.Id;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Milestone entity managed by Ebean
 */
@Entity
public class Milestone extends Model {

    private static final long serialVersionUID = 1L;
    private static Finder<Long, Milestone> find = new Finder<Long, Milestone>(
            Long.class, Milestone.class);

    @Id
    public Long id;
    @Constraints.Required
    public String versionName;
    @Constraints.Required
    @Formats.DateTime(pattern = "yyyy-MM-dd HH:mm:ss")
    public Date dueDate;
    @Constraints.Required
    public String contents;
    public Long projectId;
    public int numClosedIssues;
    public int numOpenIssues;
    public boolean isCompleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public static void create(Milestone milestone) {
        milestone.save();
    }

    public static void update(Milestone milestone, Long id) {
        milestone.update(id);
    }

    public static void delete(Long id) {
        find.ref(id).delete();
    }

    public static Milestone findById(Long id) {
        return find.byId(id);
    }

    /**
     * 모든 마일스톤이 완료일 순서대로 보인다.
     *
     * @param projectId
     * @return
     */
    public static List<Milestone> findByProjectId(Long projectId) {
        return Milestone.findMilestones(projectId, MilestoneState.ALL.state());
    }

    public static List<Milestone> findClosedMilestones(Long projectId) {
        return Milestone.findMilestones(projectId, MilestoneState.CLOSED.state());
    }

    public static List<Milestone> findOpenMilestones(Long projectId) {
        return Milestone.findMilestones(projectId, MilestoneState.OPEN.state());
    }

    public static int getCompletionRate(Milestone milestone) {
        if (milestone.numOpenIssues == 0) {
            return 0;
        }

        double closedIssueCount = new Double(milestone.numClosedIssues);
        double openIssueCount = new Double(milestone.numOpenIssues);
        double completionRate = (closedIssueCount / openIssueCount) * 100;
        return new Double(completionRate).intValue();
    }

    public String getDueDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(this.dueDate);
    }

    public static List<Milestone> findMilestones(Long projectId, String state) {
        OrderParams orderParams = new OrderParams();
        SearchParams searchParams = new SearchParams();

        switch (MilestoneState.getValue(state)) {
            case OPEN:
                orderParams.add("dueDate", Ordering.ASC);
                searchParams.add("projectId", projectId, Matching.EQUALS);
                searchParams.add("isCompleted", false, Matching.EQUALS);
                break;
            case CLOSED:
                orderParams.add("dueDate", Ordering.ASC);
                searchParams.add("projectId", projectId, Matching.EQUALS);
                searchParams.add("isCompleted", true, Matching.EQUALS);
                break;
            case ALL:
                orderParams.add("dueDate", Ordering.ASC);
                searchParams.add("projectId", projectId, Matching.EQUALS);
                break;
            default:
                return Milestone.findByProjectId(projectId);
        }

        return FinderTemplate.findBy(orderParams, searchParams, find);
    }


}
