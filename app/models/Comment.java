/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

package models;

import com.avaje.ebean.annotation.Transactional;
import models.resource.Resource;
import models.resource.ResourceConvertible;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.joda.time.Duration;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import utils.JodaDateUtil;

import javax.annotation.Nonnull;
import javax.persistence.*;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

@MappedSuperclass
abstract public class Comment extends Model implements TimelineItem, ResourceConvertible {
    private static final long serialVersionUID = 1L;

    @Id
    public Long id;

    @Lob @Constraints.Required
    public String contents;

    @Constraints.Required
    public Date createdDate;

    public Long authorId;
    public String authorLoginId;
    public String authorName;
    public Long projectId;

    @Transient
    public String parentCommentId;

    @Transient
    public String previousContents;

    public Comment() {
        createdDate = new Date();
    }

    public Comment(User author, String contents) {
        this();
        setAuthor(author);
        this.contents = contents;
    }

    public Duration ago() {
        return JodaDateUtil.ago(this.createdDate);
    }

    @Override
    abstract public Resource asResource();

    abstract public AbstractPosting getParent();

    @Transient
    public void setAuthor(User user) {
        authorId = user.id;
        authorLoginId = user.loginId;
        authorName = user.name;
    }

    @Transactional
    public void save() {
        super.save();
        updateMention();
        getParent().update();
    }

    @Transactional
    public void update() {
        super.update();
        updateMention();
    }

    protected void updateMention() {
        Mention.update(this.asResource(), NotificationEvent.getMentionedUsers(this.contents));
    }

    public void delete() {
        Attachment.deleteAll(asResource());
        NotificationEvent.deleteBy(this.asResource());
        super.delete();
        getParent().update();
    }

    public static Comparator<Comment> comparator(){
        return new Comparator<Comment>() {
            @Override
            public int compare(Comment o1, Comment o2) {
                return o1.createdDate.compareTo(o2.createdDate);
            }
        };
    }

    public Date getDate() {
        return createdDate;
    }

    abstract public Comment getParentComment();
    abstract public void setParentComment(Comment comment);
    abstract public List<? extends Comment> getSiblingComments();
    abstract public List<? extends Comment> getChildComments();

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (obj.getClass() != getClass()) {
            return false;
        }
        Comment rhs = (Comment) obj;
        return new EqualsBuilder()
                .appendSuper(super.equals(obj))
                .append(this.id, rhs.id)
                .append(this.contents, rhs.contents)
                .append(this.createdDate, rhs.createdDate)
                .append(this.authorId, rhs.authorId)
                .append(this.authorLoginId, rhs.authorLoginId)
                .append(this.authorName, rhs.authorName)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .appendSuper(super.hashCode())
                .append(id)
                .append(contents)
                .append(createdDate)
                .append(authorId)
                .append(authorLoginId)
                .append(authorName)
                .toHashCode();
    }

    public boolean isAuthoredBy(@Nonnull User user){
        return StringUtils.equalsIgnoreCase(this.authorLoginId, user.loginId);
    }

    @Override
    public String toString() {
        return "Comment{" +
                "id=" + id +
                ", contents='" + contents + '\'' +
                ", createdDate=" + createdDate +
                ", authorId=" + authorId +
                ", authorLoginId='" + authorLoginId + '\'' +
                ", authorName='" + authorName + '\'' +
                ", projectId=" + projectId +
                ", getParentComment=" + getParentComment() +
                '}';
    }
}
