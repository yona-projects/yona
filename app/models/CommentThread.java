/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Keesun Baik
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package models;

import models.enumeration.ResourceType;
import models.resource.Resource;
import models.resource.ResourceConvertible;
import models.support.ReviewSearchCondition;
import play.data.format.Formats;
import play.data.validation.Constraints;
import io.ebean.Finder;
import io.ebean.Model;

import javax.annotation.Nullable;
import jakarta.persistence.*;
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
    public static final Finder<Long, CommentThread> find = new Finder<>(CommentThread.class);

    @Id
    public Long id;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "id", column = @Column(name = "author_id")),
            @AttributeOverride(name = "loginId", column = @Column(name = "author_login_id")),
            @AttributeOverride(name = "name", column = @Column(name = "author_name")),
    })
    public UserIdent author;

    @OneToMany(mappedBy = "thread", cascade = CascadeType.REMOVE)
    public List<ReviewComment> reviewComments = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    public ThreadState state;

    @Constraints.Required
    @Formats.DateTime(pattern = "YYYY/MM/DD/hh/mm/ss")
    public Date createdDate;

    @ManyToOne
    public PullRequest pullRequest;

    public boolean isOnPullRequest() {
        return pullRequest != null;
    }

    public static List<CommentThread> findByCommitId(String commitId) {
        return find.query().where()
                .eq("commitId", commitId)
                .orderBy().desc("createdDate")
                .findList();
    }

    public static <T extends CommentThread> List<T> findByCommitId(Finder<Long, T> find,
                                                                   Project project,
                                                                   String commitId) {
        return find.query().where()
                .eq("commitId", commitId)
                .eq("project.id", project.id)
                .orderBy().desc("createdDate")
                .findList();
    }

    public static List<CommentThread> findByCommitIdAndState(String commitId, ThreadState state) {
        return find.query().where()
                .eq("commitId", commitId)
                .eq("state", state)
                .orderBy().desc("createdDate")
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

        throw new IllegalStateException("This thread has no ReviewComment.");
    }

    /**
     * Returns number of threads.
     *
     * The function finds threads matching up {@code cond} in the project having {@code projectId} and returns number of it.
     *
     * @param projectId
     * @param cond
     * @return
     */

    public static int countReviewsBy(Long projectId, @Nullable ReviewSearchCondition cond) {
        if(cond == null){
            cond = new ReviewSearchCondition();
        }
        return cond.asExpressionList(Project.find.byId(projectId)).findCount();
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

        List<CommentThread> threads = find.query().where()
                .eq("commitId", commitId)
                .eq("project.id", project.id)
                .isNull("pullRequest.id")
                .orderBy().desc("createdDate")
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

    public String getChildCommentsSizeToString(){
        if(this.reviewComments.size() > 1) {
            return String.valueOf(this.reviewComments.size() - 1);
        } else {
            return "";
        }
    }

    public boolean hasChildComments(){
        if(this.reviewComments.size() > 1) {
            return true;
        } else {
            return false;
        }
    }

    public static void deleteByPullRequest(PullRequest pullRequest) {
        for(CommentThread commentThread : find.query().where().eq("pullRequest", pullRequest).findList()) {
            commentThread.delete();
        }
    }
}
