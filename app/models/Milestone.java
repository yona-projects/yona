package models;

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

    /* can be defined outside the class */
    public static enum MilestoneState {
        OPEN("open"), CLOSED("closed"), ALL("all");
        private String state;

        MilestoneState(String state) {
            this.state = state;
        }

        public String state() {
            return this.state;
        }

        public static MilestoneState getValue(String value) {
            for (MilestoneState milestoneState : MilestoneState.values()) {
                if (milestoneState.state().equals(value)) {
                    return milestoneState;
                }
            }
            return null;
        }
    }

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

    public static Finder<Long, Milestone> find = new Finder<Long, Milestone>(
            Long.class, Milestone.class);

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
        return find.where()
                .eq("projectId", projectId)
                .orderBy("dueDate")
                .findList();
    }

    public static List<Milestone> findClosedMilestones(Long projectId) {
        return find.where()
                .eq("projectId", projectId)
                .eq("isCompleted", true)
                .orderBy("dueDate")
                .findList();
    }

    public static List<Milestone> findOpenMilestones(Long projectId) {
        return find.where()
                .eq("projectId", projectId)
                .eq("isCompleted", false)
                .orderBy("dueDate")
                .findList();
    }

    public static int getCompletionRate(Milestone milestone) {
        double closedIssueCount = new Double(milestone.numClosedIssues);
        double openIssueCount = new Double(milestone.numOpenIssues);
        double completionRate = (closedIssueCount / openIssueCount) * 100;
        return new Double(completionRate).intValue();
    }

    public String getDuedate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(this.dueDate);
    }

    public static List<Milestone> delegateFindList(Long projectId, String state) {
        switch (MilestoneState.getValue(state)) {
            case OPEN:
                return Milestone.findOpenMilestones(projectId);
            case CLOSED:
                return Milestone.findClosedMilestones(projectId);
            case ALL:
            default:
                return Milestone.findByProjectId(projectId);

        }
    }
}
