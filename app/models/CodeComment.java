package models;

import models.enumeration.ResourceType;
import models.resource.Resource;
import models.resource.ResourceConvertible;
import play.data.validation.Constraints;
import play.db.ebean.Model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.validation.constraints.Size;
import java.beans.Transient;
import java.util.Date;
import java.util.List;

@Entity
public class CodeComment extends Model implements ResourceConvertible, TimelineItem {
    private static final long serialVersionUID = 1L;
    public static final Finder<Long, CodeComment> find = new Finder<>(Long.class, CodeComment.class);

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

    public static int count(Project project, String commitId, String path){
        if(path != null){
            return CodeComment.find.where()
                    .eq("project.id", project.id)
                    .eq("commitId", commitId)
                    .eq("path", path)
                    .findRowCount();
        } else {
            return CodeComment.find.where()
                    .eq("project.id", project.id)
                    .eq("commitId", commitId)
                    .findRowCount();
        }
    }   

    public static int countByCommits(Project project, List<PullRequestCommit> commits) {
        int count = 0;
        for(PullRequestCommit commit: commits) {
            count = count + CodeComment.find.where().eq("project.id", project.id)
                                .eq("commitId", commit.getCommitId())
                                .findRowCount();
        }
        
        return count;
    }

    @Transient
    public void setAuthor(User user) {
        authorId = user.id;
        authorLoginId = user.loginId;
        authorName = user.name;
    }

    @Override
    public Resource asResource() {
        return new Resource() {
            @Override
            public String getId() {
                return id.toString();
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

    @Override
    public Date getDate() {
        return createdDate;
    }
}
