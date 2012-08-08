package models.support;

import models.enumeration.Direction;
import models.enumeration.StateType;
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
    public Long milestone;

    public SearchCondition() {
        filter = "";
        sortBy = "date";
        orderBy = Direction.DESC.direction();
        pageNum = 0;
        milestone = null;
        stateType = StateType.OPEN.name();
        commentedCheck = false;
        fileAttachedCheck = false;
        
    }
}
