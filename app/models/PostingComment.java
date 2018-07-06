/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

package models;

import models.enumeration.ResourceType;
import models.resource.Resource;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;

@Entity
public class PostingComment extends Comment {
    private static final long serialVersionUID = 1L;
    public static final Finder<Long, PostingComment> find = new Finder<>(Long.class, PostingComment.class);

    @ManyToOne
    public Posting posting;

    @OneToOne
    private PostingComment parentComment;

    public PostingComment(Posting posting, User author, String contents) {
        super(author, contents);
        this.posting = posting;
        this.projectId = posting.project.id;
    }

    /**
     * @see Comment#getParent()
     */
    public AbstractPosting getParent() {
        return posting;
    }

    @Override
    public PostingComment getParentComment() {
        return parentComment;
    }

    @Override
    public void setParentComment(Comment comment) {
        this.parentComment = (PostingComment)comment;
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

            @Override
            public Resource getContainer() {
                return posting.asResource();
            }
        };
    }
}
