package models;

import models.enumeration.ResourceType;
import models.resource.Resource;

import javax.persistence.*;

/**
 * 게시물의 댓글
 */
@Entity
public class PostingComment extends Comment {
    private static final long serialVersionUID = 1L;
    public static Finder<Long, PostingComment> find = new Finder<>(Long.class, PostingComment.class);

    @ManyToOne
    public Posting posting;

    /**
     * @see Comment#getParent()
     */
    public AbstractPosting getParent() {
        return posting;
    }

    /**
     * @see Comment#asResource()
     */
    @Override
    public Resource asResource() {
        return new Resource() {
            @Override
            public String getId() {
                return id.toString();
            }

            @Override
            public Project getProject() {
                return posting.project;
            }

            @Override
            public ResourceType getType() {
                return ResourceType.NONISSUE_COMMENT;
            }

            @Override
            public Long getAuthorId() {
                return authorId;
            }
        };
    }
}
