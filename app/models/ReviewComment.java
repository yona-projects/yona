/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Keesun Baik, Wansoon Park
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
import play.data.validation.Constraints;
import play.db.ebean.Model;

import javax.persistence.*;
import java.util.Date;
import java.util.List;

/**
 * @author Keesun Baik
 */
@Entity
public class ReviewComment extends Model implements ResourceConvertible {
    private static final long serialVersionUID = 1L;
    public static final Finder<Long, ReviewComment> find = new Finder<>(Long.class, ReviewComment.class);

    @Id
    public Long id;

    @Lob
    @Constraints.Required
    private String contents;

    @Constraints.Required
    public Date createdDate;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "id", column = @Column(name = "author_id")),
            @AttributeOverride(name = "loginId", column = @Column(name = "author_login_id")),
            @AttributeOverride(name = "name", column = @Column(name = "author_name")),
    })
    public UserIdent author;

    @ManyToOne(cascade = CascadeType.ALL)
    public CommentThread thread;

    public void setContents(String contents) {
        this.contents = contents;
    }

    public String getContents() {
        return contents;
    }

    public ReviewComment() {
        createdDate = new Date();
    }

    public static List<ReviewComment> findByThread(Long threadId) {
        return find.where()
                .eq("thread.id", threadId)
                .order().asc("createdDate")
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
                return author.id;
            }

            @Override
            public void delete() {
                ReviewComment.this.delete();
            }

            @Override
            public Resource getContainer() {
                return thread.asResource();
            }
        };
    }

    @Override
    public void delete() {
        long threadId = thread.id;
        thread.removeComment(this);

        super.delete();

        if (ReviewComment.findByThread(threadId).isEmpty()) {
            CommentThread commentThread = CommentThread.find.byId(threadId);

            PullRequest pullRequest = commentThread.pullRequest;
            if(pullRequest != null) {
                pullRequest.removeCommentThread(commentThread);
            }

            commentThread.delete();
        }


    }
}
