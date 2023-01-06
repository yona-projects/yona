/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/
package models.support;

import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.Junction;
import controllers.AbstractPostingApp;
import controllers.UserApp;
import models.*;
import models.enumeration.State;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import play.data.format.Formats;

import javax.annotation.Nonnull;
import java.text.SimpleDateFormat;
import java.util.*;

public class SearchCondition extends AbstractPostingApp.SearchCondition implements Cloneable {
    public String state;
    public Boolean commentedCheck;
    public Long milestoneId;

    public Set<Long> labelIds = new HashSet<>();
    public Long authorId;

    public Long assigneeId;
    public Project project;

    public Long mentionId;
    public Long sharerId;
    public Long favoriteId;

    public Organization organization;
    public List<String> projectNames;

    public Long commenterId;

    @Formats.DateTime(pattern = "yyyy-MM-dd")
    public Date dueDate;

    private User byUser = UserApp.currentUser();

    /**
     * This doesn't copy {@code pageNum}, because it is safe when changing tabs with page parameter.
     *
     * @return cloned instance of {@code SearchCondition}
     */
    public SearchCondition clone() {
        SearchCondition one = new SearchCondition();
        one.orderBy = this.orderBy;
        one.orderDir = this.orderDir;
        one.filter = this.filter;
        one.state = this.state;
        one.commentedCheck = this.commentedCheck;
        one.milestoneId = this.milestoneId;
        one.labelIds = new HashSet<>(this.labelIds);
        one.authorId = this.authorId;
        one.assigneeId = this.assigneeId;
        one.commenterId = this.commenterId;
        one.mentionId = this.mentionId;
        one.sharerId = this.sharerId;
        one.favoriteId = this.favoriteId;
        one.dueDate = this.dueDate;
        one.projectNames = this.projectNames;
        return one;
    }

    public SearchCondition setState(String state) {
        this.state = state;
        return this;
    }

    public SearchCondition setState(State state) {
        this.state = state.state();
        return this;
    }

    public SearchCondition setMilestoneId(Long milestoneId) {
        this.milestoneId = milestoneId;
        return this;
    }

    public SearchCondition setLabelIds(Set<Long> labelIds) {
        this.labelIds = labelIds;
        return this;
    }

    public SearchCondition setAuthorId(Long authorId) {
        this.authorId = authorId;
        return this;
    }

    public SearchCondition setAssigneeId(Long assigneeId) {
        this.assigneeId = assigneeId;
        return this;
    }

    public SearchCondition setCommenterId(Long commenterId) {
        this.commenterId = commenterId;
        return this;
    }

    public ExpressionList<Issue> asExpressionList(@Nonnull Organization organization) {
        ExpressionList<Issue> el = Issue.finder.where();

        if(isFilteredByProject()){
            el.in("project.id", getFilteredProjectIds(organization));
        } else {
            el.in("project.id", getVisibleProjectIds(organization));
        }

        el.eq("isDraft", false);

        setAssigneeIfExists(el);
        setAuthorIfExist(el);
        setMentionedIssuesIfExist(el);
        setSharedIssuesIfExist(el);
        setFavoriteIssuesIfExist(el);
        setFilteredStringIfExist(el);

        if (commentedCheck) {
            el.ge("numOfComments", AbstractPosting.NUMBER_OF_ONE_MORE_COMMENTS);
        }

        setIssueState(el);
        setOrderByIfExist(el);

        if (dueDate != null) {
            el.lt("dueDate", DateUtils.addDays(dueDate, 1));
        }

        return el;
    }

    private boolean isFilteredByProject() {
        return CollectionUtils.isNotEmpty(this.projectNames);
    }

    private List<Long> getFilteredProjectIds(Organization organization) {
        List<Long> projectIdsFilter = new ArrayList<>();
        for(String projectName: projectNames){
            for(Project project: organization.projects){
                if(project.name.equalsIgnoreCase(projectName)
                    && getVisibleProjectIds(organization).contains(project.id.toString())) {
                    projectIdsFilter.add(project.id);
                    break;
                }
            }
        }
        return projectIdsFilter;
    }

    private void setOrderByIfExist(ExpressionList<Issue> el) {
        if (StringUtils.isNotBlank(orderBy)) {
            if (orderBy.equals("dueDate")) {
                String formulaName = orderDir.equals("asc") ? "dueDateAsc" : "dueDateDesc";
                el.orderBy("weight desc, isDraft desc, " + formulaName + " " + orderDir);
            } else {
                el.orderBy("weight desc, isDraft desc, " + orderBy + " " + orderDir);
            }
        }
    }

    private void setFilteredStringIfExist(ExpressionList<Issue> el) {
        if (StringUtils.isNotBlank(filter)) {
            Junction<Issue> junction = el.disjunction();
            junction.icontains("title", filter)
                    .icontains("body", filter);
            List<Object> ids = Issue.finder.where()
                    .icontains("comments.contents", filter).findIds();
            if (!ids.isEmpty()) {
                junction.idIn(ids);
            }
            junction.endJunction();
        }
    }

    private void setAuthorIfExist(ExpressionList<Issue> el) {
        if (authorId != null) {
            el.eq("authorId", byUser.id);
        }
    }

    private void setCommenterIfExist(ExpressionList<Issue> el, Project project) {
        // TODO: access control
        if (commenterId != null) {
            User commenter = User.find.byId(commenterId);
            if(!commenter.isAnonymous()) {
                List<Long> ids = getCommentedIssueIds(byUser, project);

                updateElWhenIdsEmpty(el, ids);
            }
        }
    }

    private void setAssigneeIfExists(ExpressionList<Issue> el) {
        if (assigneeId != null) {
            if (assigneeId.equals(User.anonymous.id)) {
                el.isNull("assignee");
            } else {
                el.eq("assignee.user.id", byUser.id);
            }
        }
    }

    private List<String> getVisibleProjectIds(Organization organization) {
        List<Project> projects = organization.getVisibleProjects(byUser);
        List<String> projectsIds = new ArrayList<>();
        for (Project project : projects) {
            projectsIds.add(project.id.toString());
        }
        return projectsIds;
    }

    public SearchCondition() {
        super();
        milestoneId = null;
        state = State.OPEN.name().toLowerCase();
        orderBy = "createdDate";
        commentedCheck = false;
    }

    public ExpressionList<Issue> asExpressionList() {
        ExpressionList<Issue> el = Issue.finder.where();

        setAssigneeIfExists(el);
        setAuthorIfExist(el);
        setCommenterIfExist(el, null);
        setMentionedIssuesIfExist(el);
        setSharedIssuesIfExist(el);
        setFavoriteIssuesIfExist(el);
        setFilteredStringIfExist(el);
        setIssueState(el);
        setOrderByIfExist(el);

        el.eq("isDraft", false);

        if (commentedCheck) {
            el.ge("numOfComments", AbstractPosting.NUMBER_OF_ONE_MORE_COMMENTS);
        }

        if (dueDate != null) {
            el.lt("dueDate", DateUtils.addDays(dueDate, 1));
        }

        return el;
    }

    private void setIssueState(ExpressionList<Issue> el) {
        State st = State.getValue(state);
        if (st.equals(State.OPEN) || st.equals(State.CLOSED)) {
            el.eq("state", st);
        }
    }

    private void setMentionedIssuesIfExist(ExpressionList<Issue> el) {
        // TODO: access control
        if (mentionId != null) {
            if(!byUser.isAnonymous()) {
                List<Long> ids = Mention.getMentioningIssueIds(byUser.id);

                updateElWhenIdsEmpty(el, ids);
            }
        }
    }

    private void setSharedIssuesIfExist(ExpressionList<Issue> el) {

        if (sharerId != null) {
            if(!byUser.isAnonymous()) {
                List<Long> ids = getSharedIssueIds(byUser);

                updateElWhenIdsEmpty(el, ids);
            }
        }
    }

    private void updateElWhenIdsEmpty(ExpressionList<Issue> el, List<Long> ids) {
        if (ids.isEmpty()) {
            // No need to progress because the query matches nothing.
            el.idEq(-1);
        } else {
            el.idIn(ids);
        }
    }

    private void setFavoriteIssuesIfExist(ExpressionList<Issue> el) {
        if (favoriteId != null) {
            if(!byUser.isAnonymous()) {
                List<Long> ids = getFavoriteIssueIds(byUser);

                updateElWhenIdsEmpty(el, ids);
            }
        }
    }

    private List<Long> getCommentedIssueIds(User commenter, Project project) {
        Set<Long> issueIds = new HashSet<>();

        List<IssueComment> comments = IssueComment.find.where()
                .eq("authorId", commenter.id)
                .findList();
        if (project == null) {
            for (Comment comment : comments) {
                issueIds.add(comment.getParent().id);
            }
        } else{
            for (Comment comment : comments) {
                if (comment.projectId.equals(project.id)) {
                    issueIds.add(comment.getParent().id);
                }
            }
        }
        return new ArrayList<>(issueIds);
    }

    private List<Long> getSharedIssueIds(User user) {
        Set<Long> ids = new HashSet<>();
        List<IssueSharer> issueSharers = IssueSharer.find.where()
                .eq("user.id", user.id)
                .findList();
        for (IssueSharer issueSharer : issueSharers) {
            ids.add(issueSharer.issue.id);
        }

        return new ArrayList<>(ids);
    }

    private List<Long> getFavoriteIssueIds(User user) {
        Set<Long> ids = new HashSet<>();
        List<FavoriteIssue> favoriteIssues = FavoriteIssue.find.where()
                .eq("user.id", user.id)
                .findList();
        for (FavoriteIssue favoriteIssue : favoriteIssues) {
            ids.add(favoriteIssue.issue.id);
        }

        return new ArrayList<>(ids);
    }

    public ExpressionList<Issue> asExpressionList(Project project) {
        ExpressionList<Issue> el = Issue.finder.where();
        if( project != null ){
            el.eq("project.id", project.id);
        }
        if (StringUtils.isNotBlank(filter)) {
            Junction<Issue> junction = el.disjunction();
            junction.icontains("title", filter)
            .icontains("body", filter);
            List<Object> ids;
            if( project == null){
                ids = Issue.finder.where()
                        .icontains("comments.contents", filter).findIds();
            } else {
                ids = Issue.finder.where()
                        .eq("project.id", project.id)
                        .icontains("comments.contents", filter).findIds();
            }
            if (!ids.isEmpty()) {
                junction.idIn(ids);
            }
            junction.endJunction();
        }

        if (authorId != null) {
            if (authorId.equals(User.anonymous.id)) {
                el.isNull("authorId");
            } else {
                el.eq("authorId", authorId);
            }
        }

        if (assigneeId != null) {
            if (assigneeId.equals(User.anonymous.id)) {
                el.isNull("assignee");
            } else {
                el.eq("assignee.user.id", assigneeId);
                if(project != null) {
                    el.eq("assignee.project.id", project.id);
                }
            }
        }

        el.eq("isDraft", false);

        setCommenterIfExist(el, project);
        setSharedIssuesIfExist(el);
        setFavoriteIssuesIfExist(el);
        setIssueState(el);
        setLabelsIfExist(project, el);
        setOrderByIfExist(el);

        if (milestoneId != null) {
            if (milestoneId.equals(Milestone.NULL_MILESTONE_ID)) {
                el.isNull("milestone");
            } else {
                el.eq("milestone.id", milestoneId);
            }
        }

        if (commentedCheck) {
            el.ge("numOfComments", AbstractPosting.NUMBER_OF_ONE_MORE_COMMENTS);
        }

        if (dueDate != null) {
            el.lt("dueDate", DateUtils.addDays(dueDate, 1));
        }

        if (authorId == null && StringUtils.isBlank(filter) && labelIds.isEmpty() && assigneeId == null && mentionId == null) {
            el.isNull("parent.id");
        }

        return el;
    }

    private void setLabelsIfExist(Project project, ExpressionList<Issue> el) {
        if (CollectionUtils.isNotEmpty(labelIds)) {
            Set<IssueLabel> labels = IssueLabel.finder.where().idIn(new ArrayList<>(labelIds)).findSet();

            List<Issue> issues = Issue.finder.where()
                    .eq("project", project)
                    .in("labels", labels).findList();

            for (IssueLabel issueLabel : labels) {
                issues = findIssueByLabel(issues, issueLabel);
            }

            el.in("id", extractIssueIds(issues));
        }
    }

    private Set<Long> extractIssueIds(List<Issue> issues) {
        Set<Long> ids = new HashSet<>();
        for (Issue issue : issues) {
            ids.add(issue.id);
        }
        return ids;
    }

    private List<Issue> findIssueByLabel(List<Issue> issues, IssueLabel label) {
        List<Issue> result = new ArrayList<>();
        for (Issue issue : issues) {
            if(issue.labels.contains(label)){
                result.add(issue);
            }
        }
        return result;
    }

    public String getDueDateString() {
        if (dueDate == null) {
            return null;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(this.dueDate);
    }

    public boolean hasCondition(){
        return !(assigneeId == null
                && authorId == null
                && mentionId == null
                && commenterId == null
                && sharerId == null
                && favoriteId == null);
    }

    @Override
    public String toString() {
        return "SearchCondition{" +
                "state='" + state + '\'' +
                ", commentedCheck=" + commentedCheck +
                ", milestoneId=" + milestoneId +
                ", labelIds=" + labelIds +
                ", authorId=" + authorId +
                ", assigneeId=" + assigneeId +
                ", project=" + project +
                ", mentionId=" + mentionId +
                ", sharerId=" + sharerId +
                ", favoriteId=" + favoriteId +
                ", organization=" + organization +
                ", projectNames=" + projectNames +
                ", commenterId=" + commenterId +
                ", dueDate=" + dueDate +
                '}';
    }
}
