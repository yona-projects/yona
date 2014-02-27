package models;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import org.apache.commons.lang3.StringUtils;

/**
 * @author Keesun Baik
 */
@Entity
@DiscriminatorValue("non_ranged")
public class NonRangedCodeCommentThread extends CommentThread {

    private static final long serialVersionUID = -1L;

    public String commitId;

    public boolean isOnChangesOfPullRequest() {
        return isOnPullRequest() && StringUtils.isNotEmpty(commitId);
    }

}
