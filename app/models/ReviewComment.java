package models;

import models.enumeration.ResourceType;
import models.resource.Resource;
import play.data.validation.Constraints;
import play.db.ebean.Model;

import javax.persistence.*;
import java.util.Date;
import java.util.List;

/**
 * @author Keesun Baik
 */
@Entity
public class ReviewComment extends Model {
    public static final Finder<Long, ReviewComment> find = new Finder<>(Long.class, ReviewComment.class);

    @Id
    public Long id;

    @Lob
    @Constraints.Required
    private String contents;

    @Constraints.Required
    public Date createdDate;

    @Embedded
    public UserIdent author;

    @ManyToOne
    public CommentThread thread;

    public void setContents(String contents) {
        this.contents = contents;
    }

    public String getContents() {
        return contents;
    }

    public static List<ReviewComment> findByThread(Long threadId) {
        return find.where()
                .eq("thread.id", threadId)
                .order().desc("createdDate")
                .findList();
    }

    public Resource asResource() {
        return new Resource() {

            @Override
            public String getId() {
                return String.valueOf(id);
            }

            @Override
            public Project getProject() {
                return thread.project;
            }

            @Override
            public ResourceType getType() {
                return ResourceType.REVIEW_COMMENT;
            }

            @Override
            public Long getAuthorId() {
                return author.authorId;
            }
        };
    }
}
