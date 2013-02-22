package models;

import models.enumeration.*;
import models.support.*;
import play.data.format.*;
import play.data.validation.*;
import play.db.ebean.*;

import javax.persistence.*;
import java.text.*;
import java.util.*;

@Entity
public class Milestone extends Model {

    private static final long serialVersionUID = 1L;
    private static Finder<Long, Milestone> find = new Finder<Long, Milestone>(
            Long.class, Milestone.class);

    public static String DEFAULT_SORTER = "dueDate";

    @Id
    public Long id;

    @Constraints.Required
    public String title;

    @Constraints.Required
    @Formats.DateTime(pattern = "yyyy-MM-dd")
    public Date dueDate;

    @Constraints.Required @Lob
    public String contents;

    @Constraints.Required
    public State state;

    @ManyToOne
    public Project project;

    public static void create(Milestone milestone) {
        milestone.save();
    }

    public int getNumClosedIssues() {
        return Issue.finder.where().eq("milestoneId", this.id).eq("state", State.CLOSED).findRowCount();
    }

    public int getNumOpenIssues() {
        return Issue.finder.where().eq("milestoneId", this.id).eq("state", State.OPEN).findRowCount();
    }

    public int getNumTotalIssues() {
        return Issue.findByMilestoneId(this.id).size();
    }

    public int getCompletionRate() {
        return new Double(((double) getNumClosedIssues() / (double) getNumTotalIssues()) * 100).intValue();
    }

    public static void delete(Milestone milestone) {
        List<Issue> issues = Issue.findByMilestoneId(milestone.id);
        milestone.delete();
        for (Issue issue : issues) {
            issue.milestoneId = null;
            issue.update();
        }
    }

    public static Milestone findById(Long id) {
        return find.byId(id);
    }

    /**
     * 해당 프로젝트의 전체 마일스톤들을 찾아줍니다.
     *
     * @param projectId
     * @return
     */
    public static List<Milestone> findByProjectId(Long projectId) {
        return Milestone.findMilestones(projectId, State.ALL);
    }

    /**
     * 완료된 마일스톤들을 찾아 줍니다.
     *
     * @param projectId
     * @return
     */
    public static List<Milestone> findClosedMilestones(Long projectId) {
        return Milestone.findMilestones(projectId, State.CLOSED);
    }

    /**
     * 미완료된 마일스톤들을 찾아 줍니다.
     *
     * @param projectId
     * @return
     */
    public static List<Milestone> findOpenMilestones(Long projectId) {
        return Milestone.findMilestones(projectId, State.OPEN);
    }

    /**
     * 완료일을 yyyy-MM-dd 형식의 문자열로 변환시킵니다.
     *
     * @return
     */
    public String getDueDateString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(this.dueDate);
    }

    /**
     * sort와 direction이 없을 때는 DEFAULT_SORTER 기준으로 오른차순으로 정렬합니다.
     *
     * @param projectId
     * @param state
     * @return
     */
    public static List<Milestone> findMilestones(Long projectId,
                                                 State state) {
        return findMilestones(projectId, state, DEFAULT_SORTER, Direction.ASC);
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
    public static List<Milestone> findMilestones(Long projectId,
                                                 State state, String sort, Direction direction) {
        OrderParams orderParams = new OrderParams().add(sort, direction);
        SearchParams searchParams = new SearchParams().add("project.id", projectId, Matching.EQUALS);
        if (state != null && state != State.ALL) {
            searchParams.add("state", state, Matching.EQUALS);
        }
        return FinderTemplate.findBy(orderParams, searchParams, find);
    }

    public void updateWith(Milestone newMilestone) {
        this.contents = newMilestone.contents;
        this.title = newMilestone.title;
        this.dueDate = newMilestone.dueDate;
        this.state = newMilestone.state;
        save();
    }

    /**
     * 마일스톤의 목록을 제공합니다.
     *
     * @return
     */
    public static Map<String, String> options(Long projectId) {
        LinkedHashMap<String, String> options = new LinkedHashMap<String, String>();
        for (Milestone milestone : findMilestones(projectId, State.ALL, "title", Direction.ASC)) {
            options.put(milestone.id.toString(), milestone.title);
        }
        return options;
    }
}
