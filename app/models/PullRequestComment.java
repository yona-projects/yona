package models;

import models.enumeration.ResourceType;
import models.resource.Resource;
import models.resource.ResourceConvertible;

import org.eclipse.jgit.blame.BlameGenerator;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import playRepository.FileDiff;
import playRepository.GitRepository;

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

    @Transient
    private Boolean _isOutdated = null;

    @Transient
    private Boolean _isCommitLost = null;

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

    private boolean isCommitIdValid(Project project, String rev) {
        try {
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
            play.Logger.info(String.format("Invalid revision %s", rev), e);
            return false;
        }
    }

    public boolean hasValidCommitId() {
        return isCommitIdValid(pullRequest.toProject, commitA) &&
                isCommitIdValid(pullRequest.fromProject, commitB);
    }

    public boolean isCommitLost() throws IOException {
        try {
            getDiff();
            _isCommitLost = false;
        } catch (MissingObjectException e) {
            play.Logger.info(this + ": commit is missing", e);
            _isCommitLost = true;
        }

        return _isCommitLost;
    }

    public boolean isOutdated() throws IOException, ServletException {
        if (line == null) {
            return false;
        }

        // cache
        if (_isOutdated != null) {
            return _isOutdated;
        }

        if (path.length() > 0 && path.charAt(0) == '/') {
            path = path.substring(1);
        }

        if (commitId.equals(commitA)) {
            _isOutdated = !noChangesBetween(GitRepository.buildGitRepository(pullRequest.toProject),
                    pullRequest.toBranch, commitId, path, line);
        } else {
            if (!commitId.equals(commitB)) {
                play.Logger.warn(
                        "Invalid PullRequestComment.commitId: It must equal to commitA or commitB.");
            }
            _isOutdated = !noChangesBetween(GitRepository.buildGitRepository(pullRequest.fromProject),
                        pullRequest.fromBranch, commitId, path, line);
        }

        return _isOutdated;
    }

    static private String getLastChangedCommitUntil(
            Repository gitRepo, String rev, String path, Integer line)
            throws IOException, IllegalArgumentException {
        BlameGenerator blame = new BlameGenerator(gitRepo, path);

        if (rev == null) {
            throw new IllegalArgumentException(String.format("Null revision is not allowed"));
        }

        ObjectId id = gitRepo.resolve(rev);

        if (id == null) {
            throw new IllegalArgumentException(
                    String.format("Git object not found: revision '%s' in %s",
                            rev, gitRepo.toString()));
        }

        int typeCode = gitRepo.getObjectDatabase().newReader().open(id).getType();

        switch (typeCode) {
            case Constants.OBJ_COMMIT:
            case Constants.OBJ_TAG:
                blame.push(null, id);
                return blame.computeBlameResult().getSourceCommit(line.intValue() - 1).getName();
            default:
                throw new IllegalArgumentException(
                      String.format("Unexpected Git object type '%s' of revision '%s' in %s.",
                          Constants.encodedTypeString(typeCode), rev, gitRepo.toString()));
        }
    }

    /**
     * 저장소 {@code gitRepo}에서, {@code path}의 {@code line}이 {@code rev1}과 {@code rev2}사이에서
     * 아무 변화가 없었는지
     *
     * @param gitRepo
     * @param rev1
     * @param rev2
     * @param path
     * @param line
     * @return
     * @throws IOException
     */
    static private boolean noChangesBetween(Repository gitRepo, String rev1, String rev2,
                                            String path, Integer line) throws IOException {
        String a = getLastChangedCommitUntil(gitRepo, rev1, path, line);
        String b = getLastChangedCommitUntil(gitRepo, rev2, path, line);

        return a.equals(b);
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
            if (side.equals("context") || side.equals("add")) {
                if (path.equals(diff.pathB)) {
                    diff.updateRange(null, line);
                    fileDiff = diff;
                    return fileDiff;
                }
            } else if (side.equals("remove")) {
                if (path.equals(diff.pathA)) {
                    diff.updateRange(line, null);
                    fileDiff = diff;
                    return fileDiff;
                }
            }
        }

        play.Logger.warn(this + ": No interest diff between " + commitA + " and " +commitB);

        return null;
    }
}
