/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author Tae
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package models;

import models.enumeration.Direction;
import models.enumeration.Matching;
import models.enumeration.ResourceType;
import models.enumeration.State;
import models.resource.Resource;
import models.resource.ResourceConvertible;
import models.support.FinderTemplate;
import models.support.OrderParams;
import models.support.SearchParams;
import org.apache.commons.lang3.time.DateUtils;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import play.i18n.Messages;
import utils.JodaDateUtil;

import javax.persistence.*;
import java.text.SimpleDateFormat;
import java.util.*;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "title"}))
public class Milestone extends Model implements ResourceConvertible {

    private static final long serialVersionUID = 1L;
    public static final Finder<Long, Milestone> find = new Finder<>(Long.class, Milestone.class);

    public static final String DEFAULT_SORTER = "dueDate";

    public static final Long NULL_MILESTONE_ID = -1l;

    @Id
    public Long id;

    @Constraints.Required
    public String title;

    @Formats.DateTime(pattern = "yyyy-MM-dd")
    public Date dueDate;

    @Constraints.Required @Lob
    public String contents;

    @Constraints.Required
    public State state;

    @ManyToOne
    public Project project;

    @OneToMany(mappedBy = "milestone")
    public Set<Issue> issues;

    public void delete() {
        // Set all issues' milestone to null.
        // I don't know why Ebean does not do this by itself.
        for(Issue issue : issues) {
            issue.milestone = null;
            issue.update();
        }

        super.delete();
    }

    public static void create(Milestone milestone) {
        milestone.save();
    }

    public int getNumClosedIssues() {
        return Issue.finder.where().eq("milestone", this).eq("state", State.CLOSED).findRowCount();
    }

    public int getNumOpenIssues() {
        return Issue.finder.where().eq("milestone", this).eq("state", State.OPEN).findRowCount();
    }

    public List<Issue> sortedByNumberOfIssue(){
        List <Issue>sortedIssues = new ArrayList<>(this.issues);
        Collections.sort(sortedIssues, new Comparator<Issue>() {
            @Override
            public int compare(Issue a, Issue b) {
                return b.getNumber().compareTo(a.getNumber());
            }
        });
        return sortedIssues;
    }

    public List<Issue> sortedByNumberOfOpenIssue(){
        List<Issue> openedIssues = new ArrayList<>();
        for(Issue issue : sortedByNumberOfIssue()) {
            if(issue.isOpen()) {
                openedIssues.add(issue);
            }
        }
        return openedIssues;
    }

    public List<Issue> sortedByNumberOfClosedIssue(){
        List<Issue> closedIssues = new ArrayList<>();
        for(Issue issue : sortedByNumberOfIssue()) {
            if(issue.isClosed()) {
                closedIssues.add(issue);
            }
        }
        return closedIssues;
    }

    public int getNumTotalIssues() {
        return issues.size();
    }

    public int getCompletionRate() {
        return (int) (((double) getNumClosedIssues() / (double) getNumTotalIssues()) * 100);
    }

    public static Milestone findById(Long id) {
        return find.byId(id);
    }

    public static List<Milestone> findByProjectId(Long projectId) {
        return Milestone.findMilestones(projectId, State.ALL);
    }

    public static List<Milestone> findClosedMilestones(Long projectId) {
        return Milestone.findMilestones(projectId, State.CLOSED);
    }

    public static List<Milestone> findOpenMilestones(Long projectId) {
        return Milestone.findMilestones(projectId, State.OPEN);
    }

    /**
     * convert mildestone due date string into yyyy-MM-dd format
     *
     * @return
     */
    public String getDueDateString() {
        if (dueDate == null) {
            return null;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(this.dueDate);
    }

    /**
     * Sort milestone list by direction. 
     * If there is no direction, then sort with DEFAULT_SORTER.
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
     * find milestone with OrderParam (string for sorting)
     *
     * @param projectId
     * @param state
     * @param sort
     * @param direction
     * @return
     */
    public static List<Milestone> findMilestones(Long projectId,
                                                 State state, String sort, final Direction direction) {

        OrderParams orderParams = new OrderParams();

        if(!"completionRate".equals(sort)) {
        orderParams.add(sort, direction);
        }

        SearchParams searchParams = new SearchParams().add("project.id", projectId, Matching.EQUALS);
        if(state != null && state != State.ALL) {
            searchParams.add("state", state, Matching.EQUALS);
        }

        List<Milestone> milestones = FinderTemplate.findBy(orderParams, searchParams, find);

        if("completionRate".equals(sort)) {
            Collections.sort(milestones, new Comparator<Milestone>() {
                @Override
                public int compare(Milestone o1, Milestone o2) {
                    int o1CompletionRate = o1.getCompletionRate();
                    int o2CompletionRate = o2.getCompletionRate();

                    if(direction == Direction.ASC) {
                        return (o1CompletionRate < o2CompletionRate ? -1 : (o1CompletionRate == o2CompletionRate ? 0 : 1));
                    } else {
                        return (o1CompletionRate < o2CompletionRate ? 1 : (o1CompletionRate == o2CompletionRate ? 0 : -1));
                    }
                }
            });
        }

        return milestones;
    }

    public void updateWith(Milestone newMilestone) {
        this.contents = newMilestone.contents;
        this.title = newMilestone.title;
        this.dueDate = newMilestone.dueDate;
        this.state = newMilestone.state;
        save();
    }

    /**
     * get milestone list by hashmap.
     * key: milestone id
     * value: milestone title
     *
     * @return linkedHashMap
     */
    public static Map<String, String> options(Long projectId) {
        LinkedHashMap<String, String> options = new LinkedHashMap<>();
        for (Milestone milestone : findMilestones(projectId, State.ALL, "title", Direction.ASC)) {
            options.put(milestone.id.toString(), milestone.title);
        }
        return options;
    }

    public static boolean isUniqueProjectIdAndTitle(Long projectId, String title) {
        int count = find.where().eq("project.id", projectId).eq("title", title).findRowCount();
        return (count == 0);
    }

    public String until() {
        if (dueDate == null) {
            return null;
        }

        Date now = JodaDateUtil.now();

        if (DateUtils.isSameDay(now, dueDate)) {
            return Messages.get("common.time.today");
        } else if (isOverDueDate()) {
            return Messages.get("common.time.overday", JodaDateUtil.localDaysBetween(dueDate, now));
        } else {
            return Messages.get("common.time.leftday", JodaDateUtil.localDaysBetween(now, dueDate));
        }
    }

    public Boolean isOverDueDate(){
        return (JodaDateUtil.ago(dueDate).getMillis() > 0);
    }

    @Override
    public Resource asResource() {
        return new Resource() {
            @Override
            public String getId() {
                return id.toString();
            }

            @Override
            public Project getProject() {
                return project;
            }

            @Override
            public ResourceType getType() {
                return ResourceType.MILESTONE;
            }
        };
    }

    public void open() {
        this.state = State.OPEN;
        update();
    }

    public void close() {
        this.state = State.CLOSED;
        update();
    }

    public boolean isNullMilestone() {
        return this.id.equals(NULL_MILESTONE_ID);
    }

    public static int countOpened(Project project) {
        return find.where()
                .eq("project", project)
                .eq("state", State.OPEN)
                .findRowCount();
    }
}
