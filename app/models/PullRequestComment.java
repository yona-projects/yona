package models;

import models.enumeration.ResourceType;
import models.resource.Resource;
import models.resource.ResourceConvertible;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import playRepository.FileDiff;
import playRepository.GitRepository;

import javax.persistence.*;
import javax.servlet.ServletException;
import javax.validation.constraints.Size;
import java.io.IOException;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.ObjectUtils;

@Entity
public class PullRequestComment extends CodeComment implements ResourceConvertible, TimelineItem  {

    private static final long serialVersionUID = 1L;
    public static final Finder<Long, PullRequestComment> find = new Finder<>(Long.class, PullRequestComment.class);

    @ManyToOne
    public PullRequest pullRequest;

    public String commitA;
    public String commitB;

    @Transient
    private Boolean _isOutdated = null;

    @Transient
    private Boolean _isCommitLost = null;

    @Transient
    private Boolean _hasValidCommitId = null;

    @Transient
    private FileDiff fileDiff = null;

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
                ", commitA='" + commitA + '\'' +
                ", commitB='" + commitB + '\'' +
                ", path='" + path + '\'' +
                ", line='" + line + '\'' +
                ", side='" + side + '\'' +
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
                return pullRequest.asResource().getProject();
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

    private boolean isCommitIdValid(Project project, String rev) {
        try {
            if (StringUtils.isEmpty(rev)) {
                throw new IllegalArgumentException("An empty revision is not allowed");
            }
            Repository repo = GitRepository.buildGitRepository(project);
            ObjectId objectId = repo.resolve(rev);
            if (objectId == null) {
                play.Logger.info(String.format(
                        "Git object not found: revision '%s' in %s", rev, repo.toString()));
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            play.Logger.warn(String.format("Invalid revision %s", rev), e);
            return false;
        }
    }

    public boolean hasValidCommitId() {
        if (_hasValidCommitId != null) {
            return _hasValidCommitId;
        }

        _hasValidCommitId = isCommitIdValid(pullRequest.toProject, commitA) &&
                isCommitIdValid(pullRequest.fromProject, commitB);

        return _hasValidCommitId;
    }

    public boolean isCommitLost() throws IOException {
        if (_isCommitLost != null) {
            return _isCommitLost;
        }

        try {
            getDiff();
            _isCommitLost = false;
        } catch (MissingObjectException e) {
            play.Logger.info(this + ": commit is missing", e);
            _isCommitLost = true;
        }

        return _isCommitLost;
    }

    public boolean isOutdated() throws IOException, GitAPIException {
        if (line == null) {
            return false;
        }

        // cache
        if (_isOutdated != null) {
            return _isOutdated;
        }

        if (pullRequest.mergedCommitIdFrom == null || pullRequest.mergedCommitIdTo == null) {
            return false;
        }

        if (path.length() > 0 && path.charAt(0) == '/') {
            path = path.substring(1);
        }

        Repository mergedRepository = pullRequest.getMergedRepository();

        switch(side) {
            case A:
                return!noChangesBetween(mergedRepository,
                    pullRequest.mergedCommitIdFrom, mergedRepository, commitA, path);
            case B:
                return !noChangesBetween(mergedRepository,
                    pullRequest.mergedCommitIdTo, mergedRepository, commitB, path);
            default:
                throw new RuntimeException(unexpectedSideMessage(side));
        }
    }

    /**
     * 저장소 {@code gitRepo}에서, {@code path}가 {@code rev1}과 {@code rev2}사이에서 아무
     * 변화가 없었는지
     *
     * @param repoA
     * @param rev1
     * @param repoB
     * @param rev2
     * @param path
     * @return
     * @throws IOException
     */
    static private boolean noChangesBetween(Repository repoA, String rev1,
                                            Repository repoB, String rev2,
                                            String path) throws IOException {
        ObjectId a = getBlobId(repoA, rev1, path);
        ObjectId b = getBlobId(repoB, rev2, path);
        return ObjectUtils.equals(a, b);
    }

    static private ObjectId getBlobId(Repository repo, String rev, String path) throws IOException {
        RevTree tree = new RevWalk(repo).parseTree(repo.resolve(rev));
        TreeWalk tw = TreeWalk.forPath(repo, path, tree);
        if (tw == null) {
            return null;
        }
        return tw.getObjectId(0);
    }

    @Transient
    public FileDiff getDiff() throws IOException {
        if (fileDiff != null) {
            return fileDiff;
        }

        List<FileDiff> fileDiffs = pullRequest.getDiff(commitA, commitB);

        if (fileDiffs.size() == 0) {
            play.Logger.warn(this + ": Change not found between " + commitA + " and " + commitB);
            return null;
        }

        for (FileDiff diff: fileDiffs) {
            if ((side.equals(Side.A) && path.equals(diff.pathA)) ||
                 (side.equals(Side.B) && path.equals(diff.pathB))) {
                fileDiff = diff;
                fileDiff.interestSide = side;
                fileDiff.interestLine = line;
                return fileDiff;
            }
        }

        play.Logger.warn(this + ": No interest diff between " + commitA + " and " +commitB);

        return null;
    }

    private String unexpectedSideMessage(Side side) {
        return String.format("Expected '%s' or '%s', but '%s'", Side.A, Side.B, side);
    }

    /**
     * 이 댓글이 달린 blob의 id를 반환한다.
     *
     * @return
     * @throws IOException
     */
    private ObjectId getBlobId() throws IOException {
        Repository repo = pullRequest.getMergedRepository();
        RevTree tree = new RevWalk(repo).parseTree(repo.resolve(getCommitId()));
        TreeWalk tw = TreeWalk.forPath(repo, path, tree);
        return tw.getObjectId(0);
    }

    /**
     * 이 댓글이 주어진 댓글 {@other}와 같은 스레드에 있는지의 여부를 반환한다.
     *
     * 이 댓글이 주어진 댓글과 같은 pullrequest에 속하고, line과 side가 같으며, 댓글이 달린 blob도 같다면
     * 같은 스레드로 본다.
     *
     * @param other
     * @return
     * @throws IOException
     */
    public boolean threadEquals(PullRequestComment other) throws IOException {
        return pullRequest.equals(other.pullRequest) &&
                line.equals(other.line) &&
                side.equals(other.side) &&
                getBlobId().equals(other.getBlobId());
    }

    @Transient
    public String getCommitId() {
        switch(side) {
            case A:
                return commitA;
            case B:
                return commitB;
            default:
                throw new RuntimeException(unexpectedSideMessage(side));
        }
    }
}
