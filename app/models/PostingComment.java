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
    public static Finder<Long, PostingComment> find = new Finder<Long, PostingComment>(Long.class, PostingComment.class);

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
    public Resource asResource() {
        return new Resource() {
            @Override
            public Long getId() {
                return id;
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
