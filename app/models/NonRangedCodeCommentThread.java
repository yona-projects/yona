package models;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

/**
 * @author Keesun Baik
 */
@Entity
@DiscriminatorValue("non_ranged")
public class NonRangedCodeCommentThread extends CommentThread {

    String commitId;

}
