package models;

import models.enumeration.ResourceType;
import models.resource.Resource;
import models.resource.ResourceConvertible;

import org.eclipse.jgit.blame.BlameGenerator;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.lib.Repository;
import playRepository.FileDiff;
import playRepository.GitRepository;
import playRepository.RepositoryService;
import utils.JodaDateUtil;

import javax.persistence.*;
import javax.servlet.ServletException;
import javax.validation.constraints.Size;
import java.io.IOException;
import java.util.Date;
import java.util.List;

@Entity
public class PullRequestComment extends CodeComment implements ResourceConvertible, TimelineItem  {

    private static final long serialVersionUID = 1L;
    public static final Finder<Long, PullRequestComment> find = new Finder<>(Long.class, PullRequestComment.class);

    @ManyToOne
    public PullRequest pullRequest;

    public String commitA;
    public String commitB;


    public void authorInfos(User user) {
        this.authorId = user.id;
        this.authorLoginId = user.loginId;
        this.authorName = user.name;
    }

    @Override
    public String toString() {
        return "PullRequestComment{" +
                "id=" + id +
                ", contents='" + contents + '\'' +
                ", createdDate=" + createdDate +
                ", authorId=" + authorId +
                ", authorLoginId='" + authorLoginId + '\'' +
                ", authorName='" + authorName + '\'' +
                '}';
    }


    @Override
    public Resource asResource(){
        return new Resource() {
            @Override
            public String getId() {
                return id.toString();
            }

            @Override
            public Project getProject() {
                return null;
            }

            @Override
            public ResourceType getType() {
                return ResourceType.PULL_REQUEST_COMMENT;
            }

            @Override
            public Long getAuthorId() {
                return authorId;
            }

            public void delete() {
                PullRequestComment.this.delete();
            }
        };
    }

    public static PullRequestComment findById(Long id) {
        return find.byId(id);
    }

    @Override
    public Date getDate() {
        return createdDate;
    }

    public boolean isOutdated() throws IOException, ServletException {
        if (line == null) {
            return false;
        }

        if (!RepositoryService.VCS_GIT.equals(pullRequest.fromProject.vcs)) {
            throw new UnsupportedOperationException();
        }

        Repository gitRepo = GitRepository.buildGitRepository(pullRequest.fromProject);
        if (path.length() > 0 && path.charAt(0) == '/') {
            path = path.substring(1);
        }
        BlameGenerator blame = new BlameGenerator(gitRepo, path);
        blame.push(null, gitRepo.resolve(pullRequest.mergedCommitIdTo));
        BlameResult blameResult = blame.computeBlameResult();

        return !blameResult.getSourceCommit(line.intValue() - 1).getName().equals(commitB);
    }

    @Transient
    public List<FileDiff> getDiff() throws IOException {
        return pullRequest.getDiff(commitA, commitB);
    }
}
