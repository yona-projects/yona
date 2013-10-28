package models;

import models.enumeration.ResourceType;
import models.resource.Resource;
import models.resource.ResourceConvertible;
import play.data.validation.Constraints;
import play.db.ebean.Model;

import javax.persistence.*;
import java.beans.Transient;
import java.util.ArrayList;
import java.util.Date;

import org.joda.time.Duration;
import utils.JodaDateUtil;

import javax.validation.constraints.Size;


@MappedSuperclass
abstract public class CodeComment extends Model implements ResourceConvertible, TimelineItem {
    private static final long serialVersionUID = 1L;
    public static final Finder<Long, CodeComment> find = new Finder<>(Long.class, CodeComment.class);

    public enum Side {
        A, B
    }

    @Id
    public Long id;
    @ManyToOne
    public Project project;
    public String commitId;
    public String path;
    public Integer line; // FIXME: DB엔 integer가 아닌 bigint로 되어있음.
    @Enumerated(EnumType.STRING)
    public Side side;
    @Constraints.Required @Column(length = 4000) @Size(max=4000)
    public String contents;
    @Constraints.Required
    public Date createdDate;
    public Long authorId;
    public String authorLoginId;
    public String authorName;

    public CodeComment() {
        createdDate = new Date();
    }

    public boolean threadEquals(CodeComment other) {
        return commitId.equals(other.commitId) &&
                path.equals(other.path) &&
                line.equals(other.line) &&
                side.equals(other.side);
    }

    @Transient
    public void setAuthor(User user) {
        authorId = user.id;
        authorLoginId = user.loginId;
        authorName = user.name;
    }

    @Override
    public Date getDate() {
        return createdDate;
    }

    public Duration ago() {
        return JodaDateUtil.ago(this.createdDate);
    }

    abstract public Resource asResource();
}
