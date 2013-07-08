package models;

import models.enumeration.ResourceType;
import models.resource.Resource;
import play.data.validation.Constraints;
import play.db.ebean.Model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.validation.constraints.Size;
import java.beans.Transient;
import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: nori
 * Date: 13. 7. 4
 * Time: 오후 2:47
 * To change this template use File | Settings | File Templates.
 */
@Entity
public class CodeComment extends Model {
    public static Finder<Long, CodeComment> find
            = new Finder<Long, CodeComment>(Long.class, CodeComment.class);

    @Id
    public Long id;

    @ManyToOne
    public Project project;

    public String commitId;
    public String path;
    public Long line;
    public String side;

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

    @Transient
    public void setAuthor(User user) {
        authorId = user.id;
        authorLoginId = user.loginId;
        authorName = user.name;
    }

    public Resource asResource() {
        return new Resource() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public Project getProject() {
                return project;
            }

            @Override
            public ResourceType getType() {
                return ResourceType.CODE_COMMENT;
            }

            @Override
            public Long getAuthorId() {
                return authorId;
            }
        };
    }
}
