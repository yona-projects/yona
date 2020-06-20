/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author Tae
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;

import com.avaje.ebean.Query;
import com.avaje.ebean.RawSqlBuilder;

import models.enumeration.ResourceType;
import models.resource.Resource;

@Entity
public class IssueComment extends Comment {
    private static final long serialVersionUID = 1L;
    public static final Finder<Long, IssueComment> find = new Finder<>(Long.class, IssueComment.class);

    @ManyToOne
    public Issue issue;

    @OneToOne
    private IssueComment parentComment;

    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(
            name = "issue_comment_voter",
            joinColumns = @JoinColumn(name = "issue_comment_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    public Set<User> voters = new HashSet<>();

    public IssueComment(Issue issue, User author, String contents) {
        super(author, contents);
        this.issue = issue;
        this.projectId = issue.project.id;
    }

    /**
     * @see Comment#getParent()
     */
    public AbstractPosting getParent() {
        return issue;
    }

    @Override
    public IssueComment getParentComment() {
        return this.parentComment;
    }

    @Override
    public void setParentComment(Comment comment) {
        this.parentComment = (IssueComment)comment;
    }

    @Override
    public List<IssueComment> getSiblingComments() {
        if (parentComment == null) {
            return null;
        }

        List<IssueComment> comments = find.where()
                .eq("parentComment.id", parentComment.id)
                .findList();
        return comments;
    }

    @Override
    public List<IssueComment> getChildComments() {
        List<IssueComment> comments = find.where()
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
                return issue.project;
            }

            @Override
            public ResourceType getType() {
                return ResourceType.ISSUE_COMMENT;
            }

            @Override
            public Long getAuthorId() {
                return authorId;
            }

            @Override
            public Resource getContainer() {
                return issue.asResource();
            }
        };
    }

    public void addVoter(User user) {
        if (voters.add(user)) {
            update();
        }
    }

    public void removeVoter(User user) {
        if (voters.remove(user)) {
            update();
        }
    }

    public static IssueComment from(PostingComment postingComment, Issue issue) {
        User user = new User();
        user.id = postingComment.authorId;
        user.loginId = postingComment.authorLoginId;
        user.name = postingComment.authorName;

        String contents = postingComment.contents;

        IssueComment issueComment = new IssueComment(issue, user, contents);
        issueComment.createdDate = postingComment.createdDate;
        issueComment.authorId = postingComment.authorId;
        issueComment.authorLoginId = postingComment.authorLoginId;
        issueComment.authorName = postingComment.authorName;
        issueComment.projectId = postingComment.projectId;
        return issueComment;
    }

    public static List<IssueComment> from(Collection<PostingComment> postingComments, Issue issue) {
        List<IssueComment> issueComments = new ArrayList<>();
        for (PostingComment postingComment : postingComments) {
            issueComments.add(IssueComment.from(postingComment, issue));
        }

        return issueComments;
    }

    public static int countAllCreatedBy(User user) {
        return find.where().eq("author_id", user.id).findRowCount();
    }

    public static int countVoterOf(User user) {
        String template = "SELECT issue_comment.id " +
                "FROM issue_comment " +
                "INNER JOIN issue_comment_voter " +
                "ON issue_comment.id = issue_comment_voter.issue_comment_id " +
                "WHERE issue_comment_voter.user_id = %d";
        String sql = String.format(template, user.id);
        Set<IssueComment> set = find.setRawSql(RawSqlBuilder.parse(sql).create()).findSet();
        return set.size();
    }

    @Override
    public String toString() {
        return "IssueComment{" +
                "id=" + id +
                ", contents='" + contents + '\'' +
                ", createdDate=" + createdDate +
                ", authorId=" + authorId +
                ", authorLoginId='" + authorLoginId + '\'' +
                ", authorName='" + authorName + '\'' +
                ", projectId=" + projectId +
                '}';
    }
}
