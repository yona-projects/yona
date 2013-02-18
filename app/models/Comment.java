package models;

import models.enumeration.ResourceType;
import models.resource.Resource;

import org.joda.time.*;
import play.data.validation.*;
import play.db.ebean.*;
import utils.*;

import javax.persistence.*;
import java.util.*;

@Entity
public class Comment extends Model {
    private static final long serialVersionUID = 1L;
    private static Finder<Long, Comment> find = new Finder<Long, Comment>(Long.class, Comment.class);

    @Id
    public Long id;

    @Constraints.Required
    public String contents;

    @Constraints.Required
    public Date date;

    public String filePath;
    public Long authorId;
    public String authorLoginId;
    public String authorName;
    @ManyToOne
    public Post post;

    public Comment() {
        date = JodaDateUtil.now();
    }

    public static Comment findById(Long id) {
        return find.byId(id);
    }

    public static Long write(Comment comment) {
        comment.save();

        return comment.id;
    }

    public static List<Comment> findCommentsByPostId(Long postId) {
        return find.where().eq("post.id", postId).findList();
    }

    public static boolean isAuthor(Long currentUserId, Long id) {
        int findRowCount = find.where().eq("authorId", currentUserId).eq("id", id).findRowCount();
        return (findRowCount != 0) ? true : false;
    }

    public Duration ago(){
        return JodaDateUtil.ago(this.date);
    }

    public static void delete(Long commentId) {
        find.byId(commentId).delete();
    }

    public Resource asResource() {
        return new Resource() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public Project getProject() {
                return post.project;
            }

            @Override
            public ResourceType getType() {
                return ResourceType.BOARD_COMMENT;
            }
        };
    }
}
