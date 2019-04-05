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
import java.util.ArrayList;
import java.util.List;

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

    @Override
    public List<PostingComment> getSiblingComments() {
        if (parentComment == null) {
            return null;
        }

        List<PostingComment> comments = find.where()
                .eq("parentComment.id", parentComment.id)
                .findList();
        return comments;
    }

    @Override
    public List<PostingComment> getChildComments() {
        List<PostingComment> comments = find.where()
                .eq("parentComment.id", id)
                .findList();
        return comments;
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

    public static List<PostingComment> findAllBy(Posting posting) {
        return find.where()
                .eq("id", posting.id)
                .findList();
    }

    public static int countAllCreatedBy(User user) {
        return find.where().eq("author_id", user.id).findRowCount();
    }
}
