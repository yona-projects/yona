package models.support;

import java.util.Set;

import play.Logger;

import models.Issue;
import models.Project;
import models.User;
import models.enumeration.*;
/**
 *
 * @author Taehyun Park
 *
 */
public class SearchCondition {

    public String filter;
    public String sortBy;
    public String orderBy;
    public int pageNum;

    public String state;
    public Boolean commentedCheck;
    public Boolean fileAttachedCheck;
    public Long milestone;
    public Set<Long> labelIds;
    public String authorLoginId;
    public Long assigneeId;

    public SearchCondition() {
        filter = "";
        sortBy = "date";
        orderBy = Direction.DESC.direction();
        pageNum = 0;
        milestone = null;
        state = State.OPEN.name();
        commentedCheck = false;
        fileAttachedCheck = false;
    }

    public SearchParams asSearchParam(Project project) {
        SearchParams searchParams = new SearchParams();

        searchParams.add("project.id", project.id, Matching.EQUALS);

        if (authorLoginId != null && !authorLoginId.isEmpty()) {
            searchParams.add("authorId", User.findByLoginId(authorLoginId).id, Matching.EQUALS);
        }

        if (assigneeId != null) {
            searchParams.add("assignee.user.id", assigneeId, Matching.EQUALS);
            searchParams.add("assignee.project.id", project.id, Matching.EQUALS);
        }

        if (filter != null && !filter.isEmpty()) {
            searchParams.add("title", filter, Matching.CONTAINS);
        }

        if (milestone != null) {
            searchParams.add("milestoneId", milestone, Matching.EQUALS);
        }

        if (labelIds != null) {
            for (Long labelId : labelIds) {
                searchParams.add("labels.id", labelId, Matching.EQUALS);
            }
        }

        if (commentedCheck) {
            searchParams.add("numOfComments", Issue.NUMBER_OF_ONE_MORE_COMMENTS, Matching.GE);
        }

        State st = State.getValue(state);
        if (st.equals(State.OPEN) || st.equals(State.CLOSED)) {
            searchParams.add("state", st, Matching.EQUALS);
        }

        return searchParams;
    }
}