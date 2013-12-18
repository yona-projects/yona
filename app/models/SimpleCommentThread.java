package models;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import play.db.ebean.Model.Finder;

/**
 * @author Keesun Baik
 */
@Entity
@DiscriminatorValue("simple")
public class SimpleCommentThread extends CommentThread {
}
