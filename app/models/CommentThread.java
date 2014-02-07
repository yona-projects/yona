package models;

import models.support.ReviewSearchCondition;
import play.data.format.Formats;
import play.data.validation.Constraints;
import models.enumeration.ResourceType;
import models.resource.Resource;
import models.resource.ResourceConvertible;
import play.db.ebean.Model;
import javax.persistence.*;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Keesun Baik
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public class CommentThread extends Model implements ResourceConvertible {

    private static final long serialVersionUID = 1L;
    public static final Finder<Long, CommentThread> find = new Finder<>(Long.class, CommentThread.class);

    @Id
    public Long id;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "id", column = @Column(name = "author_id")),
            @AttributeOverride(name = "loginId", column = @Column(name = "author_login_id")),
            @AttributeOverride(name = "name", column = @Column(name = "author_name")),
    })
    public UserIdent author;

    @OneToMany(mappedBy = "thread")
    public List<ReviewComment> reviewComments = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    public ThreadState state;

    @Constraints.Required
    @Formats.DateTime(pattern = "YYYY/MM/DD/hh/mm/ss")
    public Date createdDate;

    @ManyToOne
    public PullRequest pullRequest;

    public static List<CommentThread> findByCommitId(String commitId) {
        return find.where()
                .eq("commitId", commitId)
                .order().desc("createdDate")
                .findList();
    }

    public static <T extends CommentThread> List<T> findByCommitId(Finder<Long, T> find,
                                                                   Project project,
                                                                   String commitId) {
        return find.where()
                .eq("commitId", commitId)
                .eq("project.id", project.id)
                .order().desc("createdDate")
                .findList();
    }

    public static List<CommentThread> findByCommitIdAndState(String commitId, ThreadState state) {
        return find.where()
                .eq("commitId", commitId)
                .eq("state", state)
                .order().desc("createdDate")
                .findList();
    }

    @Override
    public String toString() {
        return "CommentThread{" +
                "id=" + id +
                ", author=" + author +
                ", reviewComments=" + reviewComments +
                ", state=" + state +
                ", createdDate=" + createdDate +
                ", project=" + project +
                '}';
    }

    @ManyToOne
    public Project project;

    public Resource asResource() {
        return new Resource() {
            @Override
            public String getId() {
                return String.valueOf(id);
            }

            @Override
            public Project getProject() {
                return project;
            }

            @Override
            public ResourceType getType() {
                return ResourceType.COMMENT_THREAD;
            }

            @Override
            public Long getAuthorId() {
                return author.id;
            }
        };
    }

    public void removeComment(ReviewComment reviewComment) {
        reviewComments.remove(reviewComment);
        reviewComment.thread = null;
    }

    public enum ThreadState {
        OPEN, CLOSED;
    }

    public void addComment(ReviewComment reviewComment) {
        reviewComments.add(reviewComment);
        reviewComment.thread = this;
    }

    public ReviewComment getFirstReviewComment() {
        List<ReviewComment> list = ReviewComment.findByThread(this.id);
        if(!list.isEmpty()) {
            return list.get(0);
        }

        throw new IllegalStateException("This thread have not ReviewComment.");
    }

    /**
     * {@code projectId} 프로젝트에서 인자 조건에 따른 thread 개수를 반환한다.
     *
     * @param projectId
     * @param cond
     * @return
     */

    public static int countReviewsBy(Long projectId, ReviewSearchCondition cond) {
        return cond.asExpressionList(Project.find.byId(projectId)).findRowCount();
    }

    public static int count(PullRequest pullRequest, String commitId, String path) {
        int count = 0;

        for (CommentThread thread : CommentThread.findByCommitId(commitId)) {
            if (pullRequest != null && thread.pullRequest != pullRequest) {
                continue;
            }

            if (path != null && thread instanceof CodeCommentThread
                    && !((CodeCommentThread)thread).codeRange.path.equals(path)) {
                continue;
            }

            count++;
        }

        return count;
    }

    public static int countOnCommit(Project project, String commitId, String path) {
        int count = 0;

        List<CommentThread> threads = find.where()
                .eq("commitId", commitId)
                .eq("project.id", project.id)
                .eq("pullrequest.id", null)
                .order().desc("createdDate")
                .findList();

        for (CommentThread thread : threads) {
            if (path != null && thread instanceof CodeCommentThread
                    && !((CodeCommentThread)thread).codeRange.path.equals(path)) {
                continue;
            }

            count++;
        }

        return count;
    }
}
