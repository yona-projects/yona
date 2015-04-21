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
package models.support;

import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.Junction;
import controllers.AbstractPostingApp;
import models.*;
import models.enumeration.State;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import play.data.format.Formats;

import java.text.SimpleDateFormat;
import java.util.*;

import static models.enumeration.ResourceType.ISSUE_COMMENT;
import static models.enumeration.ResourceType.ISSUE_POST;

public class SearchCondition extends AbstractPostingApp.SearchCondition implements Cloneable {
    public String state;
    public Boolean commentedCheck;
    public Long milestoneId;

    public Set<Long> labelIds = new HashSet<>();
    public Long authorId;

    public Long assigneeId;
    public Project project;

    public Long mentionId;

    @Formats.DateTime(pattern = "yyyy-MM-dd")
    public Date dueDate;

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
        one.mentionId = this.mentionId;
        one.dueDate = this.dueDate;
        return one;
    }

    public SearchCondition updateOrderBy(String orderBy) {
        this.orderBy = orderBy;
        return this;
    }

    public SearchCondition updateOrderDir(String orderDir) {
        this.orderDir = orderDir;
        return this;
    }

    public SearchCondition updateFilter(String filter) {
        this.filter = filter;
        return this;
    }

    public SearchCondition updatePageNum(int pageNum) {
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

    public SearchCondition setMentionId(Long mentionId) {
        this.mentionId = mentionId;
        return this;
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

        if (assigneeId != null) {
            if (assigneeId.equals(User.anonymous.id)) {
                el.isNull("assignee");
            } else {
                el.eq("assignee.user.id", assigneeId);
            }
        }

        if (authorId != null) {
            el.eq("authorId", authorId);
        }

        // TODO: access control
        if (mentionId != null) {
            User mentionUser = User.find.byId(mentionId);
            if(!mentionUser.isAnonymous()) {
                List<Long> ids = getMentioningIssueIds(mentionUser);

                if (ids.isEmpty()) {
                    // No need to progress because the query matches nothing.
                    ids.add(-1l);
                    return el.idIn(ids);
                } else {
                    el.idIn(ids);
                }
            }
        }

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

        if (commentedCheck) {
            el.ge("numOfComments", AbstractPosting.NUMBER_OF_ONE_MORE_COMMENTS);
        }

        State st = State.getValue(state);
        if (st.equals(State.OPEN) || st.equals(State.CLOSED)) {
            el.eq("state", st);
        }

        if (StringUtils.isNotBlank(orderBy)) {
            if (orderBy.equals("dueDate")) {
                String formulaName = orderDir.equals("asc") ? "dueDateAsc" : "dueDateDesc";
                el.orderBy(formulaName + " " + orderDir);
            } else {
                el.orderBy(orderBy + " " + orderDir);
            }
        }

        if (dueDate != null) {
            el.lt("dueDate", DateUtils.addDays(dueDate, 1));
        }

        return el;
    }

    private List<Long> getMentioningIssueIds(User mentionUser) {
        Set<Long> ids = new HashSet<>();
        Set<Long> commentIds = new HashSet<>();

        for (Mention mention : Mention.find.where()
                .eq("user", mentionUser)
                .in("resourceType", ISSUE_POST, ISSUE_COMMENT)
                .findList()) {

            switch (mention.resourceType) {
                case ISSUE_POST:
                    ids.add(Long.valueOf(mention.resourceId));
                    break;
                case ISSUE_COMMENT:
                    commentIds.add(Long.valueOf(mention.resourceId));
                    break;
                default:
                    play.Logger.warn("'" + mention.resourceType + "' is not supported.");
                    break;
            }
        }

        if (!commentIds.isEmpty()) {
            for (IssueComment comment : IssueComment.find.where()
                    .idIn(new ArrayList<>(commentIds))
                    .findList()) {
                ids.add(comment.issue.id);
            }
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
            Set<IssueLabel> labels = IssueLabel.finder.where().idIn(new ArrayList<>(labelIds)).findSet();
            el.in("id", Issue.finder.where().in("labels", labels).findIds());
        }

        if (StringUtils.isNotBlank(orderBy)) {
            if (orderBy.equals("dueDate")) {
                String formulaName = orderDir.equals("asc") ? "dueDateAsc" : "dueDateDesc";
                el.orderBy(formulaName + " " + orderDir);
            } else {
                el.orderBy(orderBy + " " + orderDir);
            }
        }

        if (dueDate != null) {
            el.lt("dueDate", DateUtils.addDays(dueDate, 1));
        }

        return el;
    }

    public String getDueDateString() {
        if (dueDate == null) {
            return null;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(this.dueDate);
    }
}
