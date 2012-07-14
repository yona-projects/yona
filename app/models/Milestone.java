package models;

import models.enumeration.Direction;
import models.enumeration.Matching;
import models.enumeration.MilestoneState;
import models.support.FinderTemplate;
import models.support.OrderParams;
import models.support.SearchParams;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.db.ebean.Model;

import javax.persistence.Entity;
import javax.persistence.Id;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
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

    public static List<Milestone> findByProjectId(Long projectId) {
        return Milestone.findMilestones(projectId, MilestoneState.ALL);
    }

    public static List<Milestone> findClosedMilestones(Long projectId) {
        return Milestone.findMilestones(projectId, MilestoneState.CLOSED);
    }

    public static List<Milestone> findOpenMilestones(Long projectId) {
        return Milestone.findMilestones(projectId, MilestoneState.OPEN);
    }

    public int getCompletionRate() {
        if (this.numOpenIssues == 0) {
            return 0;
        }

        double closedIssueCount = new Double(this.numClosedIssues);
        double openIssueCount = new Double(this.numOpenIssues);
        double completionRate = (closedIssueCount / openIssueCount) * 100;
        return new Double(completionRate).intValue();
    }

    public String getDueDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(this.dueDate);
    }

    /**
     * sort와 direction이 없을 때는 완료일(dueDate) 기준으로 오른차순으로 정렬합니다.
     *
     * @param projectId
     * @param state
     * @return
     */
    public static List<Milestone> findMilestones(Long projectId, MilestoneState state) {
        return findMilestones(projectId, state, Direction.ASC);
    }

    /**
     * sort는 없고 direction만 있을 때는 완료일(dueDate) 기준으로 direction에 따라 정렬합니다.
     *
     * @param projectId
     * @param state
     * @param direction
     * @return
     */
    public static List<Milestone> findMilestones(Long projectId, MilestoneState state,
                                                 Direction direction) {
        OrderParams orderParams = new OrderParams()
                .add("dueDate", direction);

        SearchParams searchParams = new SearchParams()
                .add("projectId", projectId, Matching.EQUALS);

        switch (state) {
            case OPEN:
                searchParams.add("isCompleted", false, Matching.EQUALS);
                break;
            case CLOSED:
                searchParams.add("isCompleted", true, Matching.EQUALS);
                break;
        }
        return FinderTemplate.findBy(orderParams, searchParams, find);
    }

    /**
     * OrderParam이 있을 때는 해당 정렬 기준으로 정렬합니다.
     *
     * @param projectId
     * @param state
     * @param sort
     * @param direction
     * @return
     */
    public static List<Milestone> findMilestones(Long projectId, MilestoneState state, String sort,
                                                 final Direction direction) {
        List<Milestone> milestones = findMilestones(projectId, state, direction);
        if (sort == "dueDate") { // 완료일(dueDate) 기준 정렬
            return milestones;
        } else if (sort == "completionRate") {
            Collections.sort(milestones, new Comparator<Milestone>() {
                @Override
                public int compare(Milestone m1, Milestone m2) {
                    if (direction == Direction.ASC) {
                        return m1.getCompletionRate() - m2.getCompletionRate();
                    } else {
                        return m2.getCompletionRate() - m1.getCompletionRate();
                    }
                }
            });
        }
        return milestones;
    }


}
