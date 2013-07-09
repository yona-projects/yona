package models;

import models.enumeration.ResourceType;
import models.resource.Resource;
import org.joda.time.Duration;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import utils.JodaDateUtil;

import javax.persistence.*;
import javax.validation.constraints.Size;
import java.util.Date;
import java.util.List;

/**
 * @author Keesun Baik
 */
@Entity
public class SimpleComment extends Model {

    private static final long serialVersionUID = 1L;
    public static Finder<Long, SimpleComment> find = new Finder<>(Long.class, SimpleComment.class);

    @Id
    public Long id;

    @Constraints.Required @Column(length = 4000) @Size(max=4000)
    public String contents;

    @Constraints.Required
    public Date createdDate;

    public Long authorId;
    public String authorLoginId;
    public String authorName;

    /**
     * 어떤 리소스에 대한 댓글인지 나타내는 값.
     *
     * 리소스타입과 해당 리소스의 PK 값을 조합하여 되도록이면 바뀌지 않으며
     * 댓글을 달 수 있는 리소스당 유일한 값이어야 한다.
     *
     * ex) pull_request_1, pull_request_2, profile_1, milestone_1
     *
     */
    public String resourceKey;

    public SimpleComment() {
        createdDate = new Date();
    }

    public Duration ago() {
        return JodaDateUtil.ago(this.createdDate);
    }

    public void authorInfos(User user) {
        this.authorId = user.id;
        this.authorLoginId = user.loginId;
        this.authorName = user.name;
    }

    @Override
    public String toString() {
        return "SimpleComment{" +
                "id=" + id +
                ", contents='" + contents + '\'' +
                ", createdDate=" + createdDate +
                ", authorId=" + authorId +
                ", authorLoginId='" + authorLoginId + '\'' +
                ", authorName='" + authorName + '\'' +
                ", resourceKey='" + resourceKey + '\'' +
                '}';
    }

    public static List<SimpleComment> findByResourceKey(String resourceKey) {
        return find.where().eq("resourceKey", resourceKey).findList();
    }

    public Resource asResource(){
        return new Resource() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public Project getProject() {
                return null;
            }

            @Override
            public ResourceType getType() {
                return ResourceType.SIMPLE_COMMENT;
            }
        };
    }
}
