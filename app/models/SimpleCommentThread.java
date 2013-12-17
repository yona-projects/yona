package models;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

/**
 * @author Keesun Baik
 */
@Entity
@DiscriminatorValue("simple")
public class SimpleCommentThread extends CommentThread {
}
