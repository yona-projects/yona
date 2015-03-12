/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author yoon
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

import com.avaje.ebean.Ebean;
import com.avaje.ebean.Page;
import com.avaje.ebean.annotation.Formula;
import jxl.Workbook;
import jxl.format.Alignment;
import jxl.format.Border;
import jxl.format.BorderLineStyle;
import jxl.format.Colour;
import jxl.format.*;
import jxl.write.*;
import models.enumeration.ResourceType;
import models.enumeration.State;
import models.resource.Resource;
import models.support.SearchCondition;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.shiro.util.CollectionUtils;
import play.data.Form;
import play.data.format.Formats;
import play.i18n.Messages;
import utils.JodaDateUtil;

import javax.persistence.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.Boolean;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Pattern;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "number"}))
public class Issue extends AbstractPosting implements LabelOwner {
    /**
     * @author Yobi TEAM
     */
    private static final long serialVersionUID = -2409072006294045262L;

    public static final Finder<Long, Issue> finder = new Finder<>(Long.class, Issue.class);

    public static final String DEFAULT_SORTER = "createdDate";
    public static final String TO_BE_ASSIGNED = "TBA";
    public static final Pattern ISSUE_PATTERN = Pattern.compile("#\\d+");

    public State state;

    @Formats.DateTime(pattern = "yyyy-MM-dd")
    public Date dueDate;

    public static final List<State> availableStates =
            Collections.unmodifiableList(CollectionUtils.asList(State.OPEN, State.CLOSED));

    @ManyToOne
    public Milestone milestone;

    @ManyToMany(fetch = FetchType.EAGER, cascade = CascadeType.REMOVE)
    public Set<IssueLabel> labels;

    @ManyToOne
    public Assignee assignee;

    @OneToMany(cascade = CascadeType.ALL, mappedBy="issue")
    public List<IssueComment> comments;

    @OneToMany(cascade = CascadeType.ALL, mappedBy="issue")
    public List<IssueEvent> events;

    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(
            name = "issue_voter",
            joinColumns = @JoinColumn(name = "issue_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    public Set<User> voters = new HashSet<>();

    @Transient
    @Formula(select = "case when due_date is null then cast('0001-01-01 00:00:00' as timestamp) else due_date end")
    public Date dueDateDesc;

    @Transient
    @Formula(select = "case when due_date is null then cast('9999-12-31 23:59:59' as timestamp) else due_date end")
    public Date dueDateAsc;

    public Issue(Project project, User author, String title, String body) {
        super(project, author, title, body);
        this.state = State.OPEN;
    }

    public Issue() {
        super();
    }

    /**
     * @see models.AbstractPosting#computeNumOfComments()
     */
    public int computeNumOfComments() {
        return comments.size();
    }

    /**
     * @see models.Project#increaseLastIssueNumber()
     */
    @Override
    protected Long increaseNumber() {
        return Project.increaseLastIssueNumber(project.id);
    }

    protected void fixLastNumber() {
        Project.fixLastIssueNumber(project.id);
    }

    public String assigneeName() {
        return ((assignee != null && assignee.user != null) ? assignee.user.name : null);
    }

    /**
     * @see Assignee#add(Long, Long)
     */
    private void updateAssignee() {
        if (assignee != null && assignee.id == null && assignee.user.id != null) {
            assignee = Assignee.add(assignee.user.id, project.id);
        }
    }

    /**
     * @see #updateAssignee()
     */
    @Transient
    public void update() {
        updateAssignee();
        super.update();
    }

    public void checkLabels() throws IssueLabel.IssueLabelException {
        Set<IssueLabelCategory> notAllowedCategories = new HashSet<>();
        for (IssueLabel label : labels) {
            if (notAllowedCategories.contains(label.category)) {
                throw new IssueLabel.IssueLabelException("This category does " +
                        "not allow an issue to have two or more labels of " +
                        "the category");
            }

            if (label.category.isExclusive) {
                notAllowedCategories.add(label.category);
            }
        }
    }

    @Override
    public void updateProperties() {
        HashSet<String> updateProps = new HashSet<>();
        // update null milestone explicitly
        if(this.milestone == null) {
            updateProps.add("milestone");
        }
        // update null assignee explicitly
        if(this.assignee == null) {
            updateProps.add("assignee");
        }
        if(!updateProps.isEmpty()) {
            Ebean.update(this, updateProps);
        }
    }

    /**
     * @see #updateAssignee()
     */
    @Transient
    public void save() {
        updateAssignee();
        super.save();
    }

    public static int countIssues(Long projectId, State state) {
        if (state == State.ALL) {
            return finder.where().eq("project.id", projectId).findRowCount();
        } else {
            return finder.where().eq("project.id", projectId).eq("state", state).findRowCount();
        }
    }

    public static int countIssuesBy(Long projectId, SearchCondition cond) {
        return cond.asExpressionList(Project.find.byId(projectId)).findRowCount();
    }

    public static int countIssuesBy(SearchCondition cond) {
        return cond.asExpressionList().findRowCount();
    }

    public static int countIssuesBy(Long projectId, Map<String, String> paramMap) {
        Form<SearchCondition> paramForm = new Form<>(SearchCondition.class);
        SearchCondition cond = paramForm.bind(paramMap).get();

        return Issue.countIssuesBy(projectId, cond);
    }

    /**
     * Generate a Microsoft Excel file in byte array from the given issue list,
     * using JXL.
     */
    public static byte[] excelFrom(List<Issue> issueList) throws WriteException, IOException {
        WritableWorkbook workbook;
        WritableSheet sheet;

        WritableFont wf1 = new WritableFont(WritableFont.TIMES, 13, WritableFont.BOLD, false,
                UnderlineStyle.SINGLE, Colour.BLUE_GREY, ScriptStyle.NORMAL_SCRIPT);
        WritableCellFormat cf1 = new WritableCellFormat(wf1);
        cf1.setBorder(Border.ALL, BorderLineStyle.DOUBLE);
        cf1.setAlignment(Alignment.CENTRE);

        WritableFont wf2 = new WritableFont(WritableFont.TAHOMA, 11, WritableFont.NO_BOLD, false, UnderlineStyle.NO_UNDERLINE, Colour.BLACK, ScriptStyle.NORMAL_SCRIPT);
        WritableCellFormat cf2 = new WritableCellFormat(wf2);
        cf2.setShrinkToFit(true);
        cf2.setBorder(Border.ALL, BorderLineStyle.THIN);
        cf2.setAlignment(Alignment.CENTRE);

        DateFormat valueFormatDate = new DateFormat("yyyy-MM-dd HH:mm:ss");
        WritableCellFormat cfDate = new WritableCellFormat(valueFormatDate);
        cfDate.setFont(wf2);
        cfDate.setShrinkToFit(true);
        cfDate.setBorder(Border.ALL, BorderLineStyle.THIN);
        cfDate.setAlignment(Alignment.CENTRE);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        workbook = Workbook.createWorkbook(bos);
        sheet = workbook.createSheet(String.valueOf(JodaDateUtil.today().getTime()), 0);

        String[] labalArr = {"ID", "STATE", "TITLE", "ASSIGNEE", "DATE"};

        for (int i = 0; i < labalArr.length; i++) {
            sheet.addCell(new jxl.write.Label(i, 0, labalArr[i], cf1));
            sheet.setColumnView(i, 20);
        }
        for (int i = 1; i < issueList.size() + 1; i++) {
            Issue issue = issueList.get(i - 1);
            int colcnt = 0;
            sheet.addCell(new jxl.write.Label(colcnt++, i, issue.id.toString(), cf2));
            sheet.addCell(new jxl.write.Label(colcnt++, i, issue.state.toString(), cf2));
            sheet.addCell(new jxl.write.Label(colcnt++, i, issue.title, cf2));
            sheet.addCell(new jxl.write.Label(colcnt++, i, getAssigneeName(issue.assignee), cf2));
            sheet.addCell(new jxl.write.DateTime(colcnt++, i, issue.createdDate, cfDate));
        }
        workbook.write();

        try {
            workbook.close();
        } catch (WriteException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return bos.toByteArray();
    }

    private static String getAssigneeName(Assignee assignee) {
        return (assignee != null ? assignee.user.name : TO_BE_ASSIGNED);
    }

    public boolean isOpen() {
        return this.state == State.OPEN;
    }

    public boolean isClosed() {
        return this.state == State.CLOSED;
    }

    @Override
    public Resource asResource() {
        return asResource(ResourceType.ISSUE_POST);
    }

    public Resource fieldAsResource(final ResourceType resourceType) {
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
                return resourceType;
            }

            @Override
            public Resource getContainer() {
                return Issue.this.asResource();
            }
        };
    }

    public Resource stateAsResource() {
        return fieldAsResource(ResourceType.ISSUE_STATE);
    }

    public Resource milestoneAsResource() {
        return fieldAsResource(ResourceType.ISSUE_MILESTONE);
    }

    public Resource assigneeAsResource() {
        return fieldAsResource(ResourceType.ISSUE_ASSIGNEE);
    }

    public static List<Issue> findRecentlyCreated(Project project, int size) {
        return finder.where().eq("project.id", project.id)
                .order().desc("createdDate")
                .findPagingList(size).getPage(0)
                .getList();
    }

    /**
     * @see models.AbstractPosting#getComments()
     */
    @Transient
    public List<? extends Comment> getComments() {
        Collections.sort(comments, Comment.comparator());
        return comments;
    }

    public static Issue findByNumber(Project project, Long number) {
        return AbstractPosting.findByNumber(finder, project, number);
    }

    /**
     * Returns all users watching or voting the issue.
     *
     * @return The set watching and voting the issue.
     */
    @Transient
    public Set<User> getWatchers() {
        Set<User> baseWatchers = new HashSet<>();
        if (assignee != null) {
            baseWatchers.add(assignee.user);
        }
        baseWatchers.addAll(this.voters);

        return super.getWatchers(baseWatchers);
    }

    public boolean assignedUserEquals(Assignee otherAssignee) {
        if (assignee == null || assignee.user == null || assignee.user.isAnonymous()) {
            return otherAssignee == null || otherAssignee.user == null || otherAssignee.user.isAnonymous();
        }
        if (otherAssignee == null || otherAssignee.user == null || otherAssignee.user.isAnonymous()) {
            return assignee.user.isAnonymous();
        }
        return assignee.equals(otherAssignee) || assignee.user.equals(otherAssignee.user);
    }

    /**
     * @param project
     * @param days days ago
     * @return
     */
    public static List<Issue> findRecentlyOpendIssuesByDaysAgo(Project project, int days) {
        return finder.where()
                .eq("project.id", project.id)
                .eq("state", State.OPEN)
                .ge("createdDate", JodaDateUtil.before(days)).order().desc("createdDate").findList();
    }

    public static Page<Issue> findIssuesByState(int size, int pageNum, State state) {
        return finder.where().eq("state", state)
                .order().desc("createdDate")
                .findPagingList(size).getPage(pageNum);
    }

    public State previousState() {
        int currentState = Issue.availableStates.indexOf(this.state);
        if(isLastState(currentState)) {
            return Issue.availableStates.get(0);
        } else {
            return Issue.availableStates.get(currentState + 1);
        }
    }

    private boolean isLastState(int currentState) {
        return currentState + 1 == Issue.availableStates.size();
    }

    public State nextState() {
        int currentState = Issue.availableStates.indexOf(this.state);
        if(isFirstState(currentState)) {
            return Issue.availableStates.get(Issue.availableStates.size()-1);
        } else {
            return Issue.availableStates.get(currentState - 1);
        }
    }

    private boolean isFirstState(int currentState) {
        return currentState  == 0;
    }

    public State toNextState(){
        this.state = nextState();
        this.updatedDate = JodaDateUtil.now();
        super.update();
        return this.state;
    }

    @Override
    public Set<IssueLabel> getLabels() {
        return labels;
    }

    public Set<Long> getLabelIds() {
        Set<Long> labelIds = new HashSet<>();

        for(IssueLabel label : this.labels){
            labelIds.add(label.id);
        }

        return labelIds;
    }

    public List<TimelineItem> getTimeline() {
        List<TimelineItem> timelineItems = new ArrayList<>();
        timelineItems.addAll(comments);
        timelineItems.addAll(events);
        Collections.sort(timelineItems, TimelineItem.ASC);
        return timelineItems;
    }

    public boolean canBeDeleted() {
        if(this.comments == null || this.comments.isEmpty()) {
            return true;
        }

        for(IssueComment comment : comments) {
            if(!comment.authorLoginId.equals(this.authorLoginId)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Adds {@code user} as a voter.
     *
     * @param user
     */
    public void addVoter(User user) {
        if (voters.add(user)) {
            update();
        }
    }

    /**
     * Cancels the vote of {@code user}.
     *
     * @param user
     */
    public void removeVoter(User user) {
        if (voters.remove(user)) {
            update();
        }
    }

    /**
     * Returns whether {@code user} has voted or not.
     *
     * @param user
     * @return True if the user has voted, if not False
     */
    public boolean isVotedBy(User user) {
        return this.voters.contains(user);
    }

    public String getDueDateString() {
        if (dueDate == null) {
            return null;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(this.dueDate);
    }

    public Boolean isOverDueDate(){
        return (JodaDateUtil.ago(dueDate).getMillis() > 0);
    }

    public String until(){
        if (dueDate == null) {
            return null;
        }

        Date now = JodaDateUtil.now();

        if (DateUtils.isSameDay(now, dueDate)) {
            return Messages.get("common.time.today");
        } else if (isOverDueDate()) {
            return Messages.get("common.time.default.day", JodaDateUtil.localDaysBetween(dueDate, now));
        } else {
            return Messages.get("common.time.default.day", JodaDateUtil.localDaysBetween(now, dueDate));
        }
    }

    public static int countOpenIssuesByLabel(Project project, IssueLabel label) {
        return finder.where()
                .eq("project", project)
                .eq("labels", label)
                .findRowCount();
    }

    public static int countOpenIssuesByAssignee(Project project, Assignee assignee) {
        return finder.where()
                .eq("project", project)
                .eq("assignee", assignee)
                .findRowCount();
    }

    public static int countOpenIssuesByMilestone(Project project, Milestone milestone) {
        return finder.where()
                .eq("project", project)
                .eq("milestone", milestone)
                .findRowCount();
    }



}
