/**
 *  Yona, 21st Century Project Hosting SW
 *  <p>
 *  Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 *  https://yona.io
 **/
package models;

import static io.ebean.Expr.eq;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.shiro.util.CollectionUtils;

import io.ebean.Ebean;
import io.ebean.ExpressionList;
import io.ebean.PagedList;
import io.ebean.RawSqlBuilder;
import io.ebean.SqlRow;
import io.ebean.annotation.Formula;
import io.ebean.Finder;

import jxl.Workbook;
import jxl.format.Alignment;
import jxl.format.Border;
import jxl.format.BorderLineStyle;
import jxl.format.Colour;
import jxl.format.ScriptStyle;
import jxl.format.UnderlineStyle;
import jxl.format.VerticalAlignment;
import jxl.write.DateFormat;
import jxl.write.WritableCellFormat;
import jxl.write.WritableFont;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;
import models.enumeration.ResourceType;
import models.enumeration.State;
import models.resource.Resource;
import models.support.SearchCondition;
import play.data.Form;
import play.data.format.Formats;
import play.i18n.Lang;
import play.i18n.Messages;
import play.libs.typedmap.TypedMap;
import utils.LobString;
import utils.JodaDateUtil;
import utils.MessagesUtil;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "number"}))
public class Issue extends AbstractPosting implements LabelOwner {
    private static final long serialVersionUID = -2409072006294045262L;

    public static final Finder<Long, Issue> finder = new Finder<>(Issue.class);

    public static final String DEFAULT_SORTER = "createdDate";
    public static final String TO_BE_ASSIGNED = "";
    public static final Pattern ISSUE_PATTERN = Pattern.compile("#\\d+");

    public State state = State.OPEN;

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

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "issue")
    public Set<IssueSharer> sharers = new LinkedHashSet<>();

    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(
            name = "issue_voter",
            joinColumns = @JoinColumn(name = "issue_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    public Set<User> voters = new HashSet<>();

    @Transient
    @Formula(select = "case when due_date is null then cast('0001-01-01 00:00:00' as datetime) else due_date end")
    public Date dueDateDesc;

    @Transient
    @Formula(select = "case when due_date is null then cast('9999-12-31 23:59:59' as datetime) else due_date end")
    public Date dueDateAsc;

    public Issue(Project project, User author, String title, String body) {
        super(project, author, title, body);
        this.state = State.OPEN;
    }

    @Transient
    public String targetProjectId;

    @Transient
    public String parentIssueId;

    @Transient
    public String referCommentId;

    @OneToOne
    public Issue parent;

    public Integer weight = 0;

    public Boolean isDraft = false;

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

    public Long milestoneId() {
        if (milestone == null) {
            return Milestone.NULL_MILESTONE_ID;
        }
        return milestone.id;
    }

    public boolean hasAssignee() {
        return (assignee != null && assignee.user != null);
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
            io.ebean.UpdateQuery<Issue> update = Ebean.update(Issue.class);
            for (String updateProp : updateProps) {
                update.setNull(updateProp);
            }
            update.where().idEq(id).update();
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

    public static int countAllAssignedBy(User user) {
        String template = "SELECT issue.id FROM issue \n" +
                "INNER JOIN assignee ON issue.assignee_id = assignee.id \n" +
                "WHERE assignee.user_id = %d";
        String sql = String.format(template, user.id);
        Set<Issue> set = finder.query().setRawSql(RawSqlBuilder.parse(sql).create()).findSet();
        return set.size();
    }

    public static int countVoterOf(User user) {
        String template = "SELECT issue.id " +
                "FROM issue " +
                "INNER JOIN issue_voter " +
                "ON issue.id = issue_voter.issue_id " +
                "WHERE issue_voter.user_id = %d";
        String sql = String.format(template, user.id);
        Set<Issue> set = finder.query().setRawSql(RawSqlBuilder.parse(sql).create()).findSet();
        return set.size();
    }

    public static int countAllCreatedBy(User user) {
        return finder.query().where().eq("author_id", user.id).findCount();
    }

    public static int countIssues(Long projectId, State state) {
        if (state == State.ALL) {
            return finder.query().where().eq("project.id", projectId).isNull("parent.id").ne("state", State.DRAFT).findCount();
        } else {
            return finder.query().where().eq("project.id", projectId).isNull("parent.id").eq("state", state).findCount();
        }
    }

    public static int countIssuesBy(Long projectId, SearchCondition cond) {
        return cond.asExpressionList(Project.find.byId(projectId)).findCount();
    }

    public static int countIssuesBy(SearchCondition cond) {
        return cond.asExpressionList().findCount();
    }

    public static int countIssuesBy(Long projectId, Map<String, String> paramMap) {
        Form<SearchCondition> paramForm = utils.FormUtil.form(SearchCondition.class);
        SearchCondition cond = paramForm.bind(
                Lang.forCode(utils.RequestUtil.languageCode(utils.LegacyRequestContext.currentRequestOrNull())),
                TypedMap.empty(),
                paramMap).get();

        return Issue.countIssuesBy(projectId, cond);
    }

    public static int countIssuesBy(Organization organization, SearchCondition cond) {
        return cond.asExpressionList(organization).findCount();
    }

    /**
     * Generate a Microsoft Excel file in byte array from the given issue list,
     * using JXL.
     */
    public static byte[] excelFrom(List<Issue> issueList) throws WriteException, IOException {
        WritableWorkbook workbook;
        WritableSheet sheet;

        WritableCellFormat headerCellFormat = getHeaderCellFormat();
        WritableCellFormat bodyCellFormat = getBodyCellFormat();
        WritableCellFormat dateCellFormat = getDateCellFormat();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        workbook = Workbook.createWorkbook(bos);
        sheet = workbook.createSheet(String.valueOf(JodaDateUtil.today().getTime()), 0);

        String[] titles = {"No",
                MessagesUtil.get("issue.state"),
                MessagesUtil.get("title"),
                MessagesUtil.get("issue.assignee"),
                MessagesUtil.get("issue.content"),
                MessagesUtil.get("issue.label"),
                MessagesUtil.get("issue.createdDate"),
                MessagesUtil.get("issue.dueDate"),
                MessagesUtil.get("milestone"),
                "URL",
                MessagesUtil.get("common.comment"),
                MessagesUtil.get("common.comment.author"),
                MessagesUtil.get("common.comment.created")};

        for (int i = 0; i < titles.length; i++) {
            sheet.addCell(new jxl.write.Label(i, 0, titles[i], headerCellFormat));
            sheet.setColumnView(i, 20);
        }
        int lineNumber = 0;
        for (int idx = 1; idx < issueList.size() + 1; idx++) {
            Issue issue = issueList.get(idx - 1);
            List<IssueComment> comments = issue.comments;

            lineNumber++;
            int columnPos = 0;
            String milestoneName = issue.milestone != null ? issue.milestone.title : "";
            sheet.addCell(new jxl.write.Label(columnPos++, lineNumber, issue.getNumber().toString(), bodyCellFormat));
            sheet.addCell(new jxl.write.Label(columnPos++, lineNumber, issue.state.toString(), bodyCellFormat));
            sheet.addCell(new jxl.write.Label(columnPos++, lineNumber, issue.title, bodyCellFormat));
            sheet.addCell(new jxl.write.Label(columnPos++, lineNumber, getAssigneeName(issue.assignee), bodyCellFormat));
            sheet.addCell(new jxl.write.Label(columnPos++, lineNumber, issue.body, bodyCellFormat));
            sheet.addCell(new jxl.write.Label(columnPos++, lineNumber, getIssueLabels(issue), bodyCellFormat));
            sheet.addCell(new jxl.write.DateTime(columnPos++, lineNumber, issue.createdDate, dateCellFormat));
            sheet.addCell(new jxl.write.Label(columnPos++, lineNumber, JodaDateUtil.geYMDDate(issue.dueDate), bodyCellFormat));
            sheet.addCell(new jxl.write.Label(columnPos++, lineNumber, milestoneName, bodyCellFormat));
            sheet.addCell(new jxl.write.Label(columnPos++, lineNumber, controllers.routes.IssueApp.issue(issue.project.owner, issue.project.name, issue.number).toString(), bodyCellFormat));
            if (comments.size() > 0) {
                for (int j = 0; j < comments.size(); j++) {
                    sheet.addCell(new jxl.write.Label(columnPos, lineNumber + j, comments.get(j).contents, bodyCellFormat));
                    sheet.addCell(new jxl.write.Label(columnPos+1, lineNumber + j, comments.get(j).authorName, bodyCellFormat));
                    sheet.addCell(new jxl.write.DateTime(columnPos + 2, lineNumber + j, comments.get(j).createdDate, dateCellFormat));
                }
                lineNumber = lineNumber + comments.size() - 1;
            }
        }

        workbook.write();
        try {
            workbook.close();
        } catch (WriteException | IOException e) {
            e.printStackTrace();
        }

        return bos.toByteArray();
    }

    private static String getIssueLabels(Issue issue) {
        StringBuilder labels = new StringBuilder();
        for(IssueLabel issueLabel: issue.getLabels()){
            labels.append(issueLabel.name + ", ");
        }
        return labels.toString().replaceAll(", $", "");
    }

    private static WritableCellFormat getDateCellFormat() throws WriteException {
        WritableFont baseFont= new WritableFont(WritableFont.ARIAL, 12, WritableFont.NO_BOLD, false, UnderlineStyle.NO_UNDERLINE, Colour.BLACK, ScriptStyle.NORMAL_SCRIPT);
        DateFormat valueFormatDate = new DateFormat("yyyy-MM-dd HH:mm");
        WritableCellFormat cellFormat = new WritableCellFormat(valueFormatDate);
        cellFormat.setFont(baseFont);
        cellFormat.setShrinkToFit(true);
        cellFormat.setAlignment(Alignment.CENTRE);
        cellFormat.setVerticalAlignment(VerticalAlignment.TOP);
        return cellFormat;
    }

    private static WritableCellFormat getBodyCellFormat() throws WriteException {
        WritableFont baseFont = new WritableFont(WritableFont.ARIAL, 12, WritableFont.NO_BOLD, false, UnderlineStyle.NO_UNDERLINE, Colour.BLACK, ScriptStyle.NORMAL_SCRIPT);
        return getBodyCellFormat(baseFont);
    }

    private static WritableCellFormat getBodyCellFormat(WritableFont baseFont) throws WriteException {
        WritableCellFormat cellFormat = new WritableCellFormat(baseFont);
        cellFormat.setBorder(Border.NONE, BorderLineStyle.THIN);
        cellFormat.setVerticalAlignment(VerticalAlignment.TOP);
        return cellFormat;
    }

    private static WritableCellFormat getHeaderCellFormat() throws WriteException {
        WritableFont headerFont = new WritableFont(WritableFont.ARIAL, 14, WritableFont.BOLD, false,
                UnderlineStyle.NO_UNDERLINE, Colour.BLACK, ScriptStyle.NORMAL_SCRIPT);
        WritableCellFormat headerCell = new WritableCellFormat(headerFont);
        headerCell.setBorder(Border.ALL, BorderLineStyle.THIN);
        headerCell.setAlignment(Alignment.CENTRE);
        return headerCell;
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
        return finder.query().where().eq("project.id", project.id)
                .orderBy().desc("createdDate")
                .setFirstRow((0) * (size)).setMaxRows(size).findPagedList()
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
        Issue issue = Issue.finder.query()
                .fetch("project")
                .fetch("project.menuSetting")
                .fetch("labels")
                .fetch("sharers")
                .fetch("assignee")
                .fetch("assignee.user")
                .fetch("milestone")
                .fetch("parent")
                .fetch("parent.project")
                .fetch("parent.project.menuSetting")
                .fetch("parent.assignee")
                .fetch("parent.assignee.user")
                .where()
                .eq("project.id", project.id)
                .eq("number", number)
                .findOne();
        if (issue != null) {
            if (issue.body == null || LobString.needsUnwrap(issue.body)) {
                SqlRow row = Ebean.createSqlQuery("select body from issue where id = :id")
                        .setParameter("id", issue.id)
                        .findOne();
                if (row != null) {
                    issue.body = LobString.unwrap(row.getString("body"));
                }
            }
            if (issue.voters == null) {
                issue.voters = new HashSet<>();
            }
            if (issue.labels == null) {
                issue.labels = new HashSet<>();
            }
            if (issue.sharers == null) {
                issue.sharers = new LinkedHashSet<>();
            }
            if (issue.comments == null) {
                issue.comments = IssueComment.find.query().where()
                        .eq("issue.id", issue.id)
                        .findList();
            }
            loadCommentContents(issue.comments);
            if (issue.events == null) {
                issue.events = new ArrayList<>();
            }
        }
        return issue;
    }

    public static Issue findByIdWithProject(Long id) {
        return finder.query()
                .fetch("project")
                .fetch("project.menuSetting")
                .fetch("assignee")
                .fetch("assignee.user")
                .where()
                .idEq(id)
                .findOne();
    }

    private static void loadCommentContents(List<IssueComment> comments) {
        for (IssueComment comment : comments) {
            if ((comment.contents == null || LobString.needsUnwrap(comment.contents)) && comment.id != null) {
                SqlRow row = Ebean.createSqlQuery("select contents from issue_comment where id = :id")
                        .setParameter("id", comment.id)
                        .findOne();
                if (row != null) {
                    comment.contents = LobString.unwrap(row.getString("contents"));
                }
            }
        }
    }

    public static List<Issue> findByMilestone(Milestone milestone) {
        return finder.query().where().eq("milestone.id", milestone.id).findList();
    }

    public static List<Issue> findClosedIssuesByMilestone(Milestone milestone) {
        return finder.query().where().eq("milestone.id", milestone.id).eq("state", State.CLOSED).findList();
    }

    public static List<Issue> findOpenIssuesByMilestone(Milestone milestone) {
        return finder.query().where().eq("milestone.id", milestone.id).eq("state", State.OPEN).findList();
    }

    @Transient
    public Set<User> getWatchers() {
        return getWatchers(true);
    }

    /**
     * Returns all users watching or voting the issue.
     *
     * @return The set watching and voting the issue.
     */
    @Transient
    public Set<User> getWatchers(boolean allowedWatchersOnly) {
        Set<User> baseWatchers = new HashSet<>();
        if (assignee != null) {
            baseWatchers.add(assignee.user);
        }
        if (this.voters != null) {
            baseWatchers.addAll(this.voters);
        }

        return super.getWatchers(baseWatchers, allowedWatchersOnly);
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
    public static List<Issue> findRecentlyIssuesByDaysAgo(User user, int days) {
        return finder.query()
                .fetch("project")
                .fetch("project.menuSetting")
                .fetch("assignee")
                .fetch("assignee.user")
                .where()
                .or(eq("assignee.user.id", user.id), eq("authorId", user.id))
                .ge("updatedDate", JodaDateUtil.before(days))
                .orderBy("updatedDate desc, state asc").findList();
    }

    public static List<Issue> findByProject(Project project, String filter) {
        ExpressionList<Issue> el = finder.query().where()
                .eq("project.id", project.id);
        if(StringUtils.isNotEmpty(filter)){
            el.icontains("title", filter);
        }
        return el.orderBy().desc("createdDate").findList();
    }

    public static List<Issue> findByProject(Project project, String filter, int limit) {
        ExpressionList<Issue> el = finder.query().where()
                .eq("project.id", project.id);
        if(StringUtils.isNotEmpty(filter)){
            el.icontains("title", filter);
        }
        return el.setMaxRows(limit).orderBy().desc("createdDate").findList();
    }

    public static List<Issue> findParentIssueByProject(Project project, String filter, int limit) {
        ExpressionList<Issue> el = finder.query().where()
                .eq("project.id", project.id)
                .isNull("parent");
        if(StringUtils.isNotEmpty(filter)){
            el.icontains("title", filter);
        }
        return el.setMaxRows(limit).orderBy().desc("createdDate").findList();
    }

    public static PagedList<Issue> findIssuesByState(int size, int pageNum, State state) {
        return finder.query().where().eq("state", state)
                .orderBy().desc("createdDate")
                .setFirstRow((pageNum) * (size)).setMaxRows(size).findPagedList();
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
        if(isFirstState(currentState) || this.state == State.DRAFT) {
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
        return isOpen() && (JodaDateUtil.ago(dueDate).getMillis() > 0);
    }

    public String until(){
        if (dueDate == null) {
            return null;
        }

        Date now = JodaDateUtil.now();

        if (DateUtils.isSameDay(now, dueDate)) {
            return MessagesUtil.get("common.time.today");
        } else if (isOverDueDate()) {
            return MessagesUtil.get("common.time.default.day", JodaDateUtil.localDaysBetween(dueDate, now));
        } else {
            return MessagesUtil.get("common.time.default.day", JodaDateUtil.localDaysBetween(now, dueDate));
        }
    }

    public static int countOpenIssuesByLabel(Project project, IssueLabel label) {
        return finder.query().where()
                .eq("project", project)
                .eq("labels", label)
                .eq("state", State.OPEN)
                .findCount();
    }

    public static int countOpenIssuesByAssignee(Project project, Assignee assignee) {
        return finder.query().where()
                .eq("project", project)
                .eq("assignee", assignee)
                .eq("state", State.OPEN)
                .findCount();
    }

    public static int countOpenIssuesByMilestone(Project project, Milestone milestone) {
        return finder.query().where()
                .eq("project", project)
                .eq("milestone", milestone)
                .eq("state", State.OPEN)
                .findCount();
    }

    public static List<Issue> findByParentIssueId(Long parentIssueId){
        return finder.query()
                .fetch("project")
                .fetch("project.menuSetting")
                .fetch("assignee")
                .fetch("assignee.user")
                .where()
                .eq("parent.id", parentIssueId)
                .findList();
    }

    public boolean hasChildIssue(){
        return finder.query().where()
                .eq("parent.id", this.id)
                .eq("isDraft", false)
                .setMaxRows(1)
                .findCount() > 0;
    }

    public boolean hasParentIssue(){
        return parent != null && finder.query().where()
                .isNotNull("parent.id")
                .findCount() > 0;
    }

    public static List<Issue> findByParentIssueIdAndState(Long parentIssueId, State state){
        return finder.query()
                .fetch("project")
                .fetch("project.menuSetting")
                .fetch("assignee")
                .fetch("assignee.user")
                .where()
                .eq("parent.id", parentIssueId)
                .eq("state", state)
                .orderBy("number")
                .findList();
    }

    public static int countByParentIssueIdAndState(Long parentIssueId, State state){
        return finder.query().where()
                .eq("parent.id", parentIssueId)
                .eq("state", state)
                .findCount();
    }

    public static int countOpenIssuesByUser(User user) {
        return finder.query().where()
                .eq("assignee.user.id", user.id)
                .eq("state", State.OPEN)
                .findCount();
    }

    public IssueSharer findSharerByUserId(Long id){
        for (IssueSharer sharer : sharers) {
            if (sharer.user.id.equals(id)) {
                return sharer;
            }
        }
        return null;
    }

    public IssueComment findCommentByCommentId(Long id) {
        for (IssueComment comment: comments) {
            if (comment.id.equals(id)) {
                return comment;
            }
        }
        return null;
    }

    public List<IssueSharer> getSortedSharer() {
        return new ArrayList<>(sharers);
    }

    public static int getCountOfMentionedOpenIssues(Long userId) {
        return finder.query().where()
                .in("id", Mention.getMentioningIssueIds(userId))
                .eq("state", State.OPEN)
                .findCount();
    }

    public static Issue from(Posting posting) {
        Issue issue = new Issue();

        issue.title = posting.title;
        issue.body = posting.body;
        issue.history = posting.history;
        issue.createdDate = posting.createdDate;
        issue.updatedDate = posting.updatedDate;
        issue.authorId = posting.authorId;
        issue.authorLoginId = posting.authorLoginId;
        issue.authorName = posting.authorName;
        issue.project = posting.project;

        return issue;
    }
}
