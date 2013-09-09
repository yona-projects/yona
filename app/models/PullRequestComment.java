package models;

import models.enumeration.ResourceType;
import models.resource.Resource;
import models.resource.ResourceConvertible;

import org.eclipse.jgit.blame.BlameGenerator;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.diff.*;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.DepthWalk;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.joda.time.Duration;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import playRepository.GitRepository;
import playRepository.PlayRepository;
import playRepository.RepositoryService;
import utils.JodaDateUtil;

import javax.persistence.*;
import javax.servlet.ServletException;
import javax.validation.constraints.Size;
import java.io.IOException;
import java.util.Date;
import java.util.List;

@Entity
public class PullRequestComment extends Model implements ResourceConvertible, TimelineItem  {

    private static final long serialVersionUID = 1L;
    public static final Finder<Long, PullRequestComment> find = new Finder<>(Long.class, PullRequestComment.class);

    @Id
    public Long id;

    @ManyToOne
    public PullRequest pullRequest;

    public String commitA;
    public String commitB;
    public String path;
    public String side;
    public Integer line;

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

    public PullRequestComment() {
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
        return "PullRequestComment{" +
                "id=" + id +
                ", contents='" + contents + '\'' +
                ", createdDate=" + createdDate +
                ", authorId=" + authorId +
                ", authorLoginId='" + authorLoginId + '\'' +
                ", authorName='" + authorName + '\'' +
                ", resourceKey='" + resourceKey + '\'' +
                '}';
    }

    public static List<PullRequestComment> findByResourceKey(String resourceKey) {
        return find.where()
                .eq("resourceKey", resourceKey)
                .orderBy().asc("createdDate")
                .findList();
    }

    public static int countByResourceKey(String resourceKey) {
        return find.where()
                .eq("resourceKey", resourceKey)
                .findRowCount();
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

        return !blameResult.getSourceCommit(line - 1).getName().equals(commitB);
    }

    /*
    public String getCodeDiff() throws IOException {
        Repository gitRepo = GitRepository.buildGitRepository(pullRequest.fromProject);
        return GitRepository.getPatch(gitRepo, commitB, commitA); // commitB가 아니라 이 댓글을 남긴 시점에서의
        // commit id여야 하는 것이 아닌지. commitB는 해당 시점에서 해당 라인의 최종 변경 커밋 id인데...
    }
    */

    public class Diff {
        public RawText a;
        public RawText b;
        public EditList editList;
        public String commitA;
        public String commitB;
        public String path;
    }

    public Diff getCodeDiff() throws IOException {
        Repository repository = GitRepository.buildGitRepository(pullRequest.fromProject);

        RevTree treeA = new RevWalk(repository).parseTree(repository.resolve(commitA));
        RevTree treeB = new RevWalk(repository).parseTree(repository.resolve(commitB));

        if (path.length() > 0 && path.charAt(0) == '/') {
            path = path.substring(1);
        }

        TreeWalk t1 = TreeWalk.forPath(repository, path, treeA);
        TreeWalk t2 = TreeWalk.forPath(repository, path, treeB);

        ObjectId blobA = t1.getObjectId(0);
        ObjectId blobB = t2.getObjectId(0);

        byte[] rawA = repository.open(blobA).getBytes();
        byte[] rawB = repository.open(blobB).getBytes();
        RawText a = new RawText(rawA);
        RawText b = new RawText(rawB);

        DiffAlgorithm diffAlgorithm = DiffAlgorithm.getAlgorithm(
        repository.getConfig().getEnum(
            ConfigConstants.CONFIG_DIFF_SECTION, null,
            ConfigConstants.CONFIG_KEY_ALGORITHM,
            DiffAlgorithm.SupportedAlgorithm.HISTOGRAM));

        Diff diff = new Diff();
        diff.a = a;
        diff.b = b;
        diff.commitA = commitA;
        diff.commitB = commitB;
        diff.path = path;
        diff.editList = diffAlgorithm.diff(RawTextComparator.DEFAULT, a, b);

        return diff;
    }
}
