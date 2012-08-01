package models.support;

import models.enumeration.Direction;
import models.enumeration.IssueStateType;
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
    
    public String stateType;
    public Boolean commentedCheck;
    public Boolean fileAttachedCheck;
    public long milestoneId;

    public SearchCondition() {
        filter = "";
        sortBy = "date";
        orderBy = Direction.DESC.direction();
        pageNum = 0;
        stateType = IssueStateType.OPEN.name();
        commentedCheck = false;
        fileAttachedCheck = false;
        
    }
}
