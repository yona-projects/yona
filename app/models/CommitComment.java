package models;

import models.enumeration.ResourceType;
import models.resource.Resource;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Entity;
import javax.validation.constraints.Size;

@Entity
public class CommitComment extends CodeComment {
    private static final long serialVersionUID = 1L;
    public static final Finder<Long, CommitComment> find = new Finder<>(Long.class, CommitComment.class);

    public CommitComment() {
        super();
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
                return ResourceType.COMMIT_COMMENT;
            }

            @Override
            public Long getAuthorId() {
                return authorId;
            }

            public void delete() {
                CommitComment.this.delete();
            }
        };
    }

    public static int count(Project project, String commitId, String path){
        if(path != null){
            return CommitComment.find.where()
                    .eq("project.id", project.id)
                    .eq("commitId", commitId)
                    .eq("path", path)
                    .findRowCount();
        } else {
            return CommitComment.find.where()
                    .eq("project.id", project.id)
                    .eq("commitId", commitId)
                    .findRowCount();
        }
    }

    public static int countByCommits(Project project, List<PullRequestCommit> commits) {
        int count = 0;
        for(PullRequestCommit commit: commits) {
            count += CommitComment.find.where().eq("project.id", project.id)
                                .eq("commitId", commit.getCommitId())
                                .findRowCount();
        }

        return count;
    }

    public static List<CommitComment> findByCommits(Project project, List<PullRequestCommit> commits) {
        List<CommitComment> list = new ArrayList<>();
        for(PullRequestCommit commit: commits) {
            list.addAll(CommitComment.find.where().eq("project.id", project.id).eq("commitId", commit.getCommitId()).findList());
        }
        return list;
    }
}
