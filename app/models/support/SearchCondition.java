package models.support;

import java.util.Set;

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
}
