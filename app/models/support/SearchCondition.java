package models.support;

import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.Junction;

import controllers.AbstractPostingApp;
import controllers.IssueApp;
import models.*;
import models.enumeration.State;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import utils.LabelSearchUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.avaje.ebean.Expr.icontains;

public class SearchCondition extends AbstractPostingApp.SearchCondition {
    public String state;
    public Boolean commentedCheck;
    public Long milestoneId;

    public Set<Long> labelIds = new HashSet<>();
    public Long authorId;

    public Long assigneeId;

    public SearchCondition clone() {
        SearchCondition one = new SearchCondition();
        one.orderBy = this.orderBy;
        one.orderDir = this.orderDir;
        one.filter = this.filter;
        one.pageNum = one.pageNum;
        one.state = this.state;
        one.commentedCheck = this.commentedCheck;
        one.milestoneId = this.milestoneId;
        one.labelIds = new HashSet<Long>(this.labelIds);
        one.authorId = this.authorId;
        one.assigneeId = this.assigneeId;
        return one;
    }

    public SearchCondition setOrderBy(String orderBy) {
        this.orderBy = orderBy;
        return this;
    }

    public SearchCondition setOrderDir(String orderDir) {
        this.orderDir = orderDir;
        return this;
    }

    public SearchCondition setFilter(String filter) {
        this.filter = filter;
        return this;
    }

    public SearchCondition setPageNum(int pageNum) {
        this.pageNum = pageNum;
        return this;
    }

    public SearchCondition setState(String state) {
        this.state = state;
        return this;
    }

    public SearchCondition setState(State state) {
        this.state = state.state();
        return this;
    }

    public SearchCondition setCommentedCheck(Boolean commentedCheck) {
        this.commentedCheck = commentedCheck;
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

    public SearchCondition addLabelId(Long labelId) {
        labelIds.add(labelId);
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

    public SearchCondition() {
        super();
        milestoneId = null;
        state = State.OPEN.name();
        commentedCheck = false;
    }

    public ExpressionList<Issue> asExpressionList(Project project) {
        ExpressionList<Issue> el = Issue.finder.where().eq("project.id", project.id);

        if (StringUtils.isNotBlank(filter)) {
            Junction<Issue> junction = el.disjunction();
            junction.icontains("title", filter)
            .icontains("body", filter);
            List<Object> ids = Issue.finder.where()
                    .eq("project.id", project.id)
                    .icontains("comments.contents", filter).findIds();
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
                el.eq("assignee.project.id", project.id);
            }
        }

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

        State st = State.getValue(state);
        if (st.equals(State.OPEN) || st.equals(State.CLOSED)) {
            el.eq("state", st);
        }

        if (CollectionUtils.isNotEmpty(labelIds)) {
            el.add(LabelSearchUtil.createLabelSearchExpression(el.query(), labelIds));
        }

        if (orderBy != null) {
            el.orderBy(orderBy + " " + orderDir);
        }

        return el;
    }
}
