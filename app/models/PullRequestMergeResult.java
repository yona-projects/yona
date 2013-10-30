package models;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import playRepository.GitCommit;
import playRepository.GitConflicts;

/**
 * 보낸코드 병합 결과
 */
public class PullRequestMergeResult {
    private GitConflicts gitConflicts;
    private List<GitCommit> gitCommits;
    private List<PullRequestCommit> newCommits;
    private PullRequest pullRequest;

    public GitConflicts getGitConflicts() {
        return gitConflicts;
    }
    public void setGitConflicts(GitConflicts gitConflicts) {
        this.gitConflicts = gitConflicts;
    }
    public List<GitCommit> getGitCommits() {
        return gitCommits;
    }
    public void setGitCommits(List<GitCommit> commits) {
        this.gitCommits = commits;
    }
    public PullRequest getPullRequest() {
        return pullRequest;
    }
    public void setPullRequest(PullRequest pullRequest) {
        this.pullRequest = pullRequest;
    }
    public boolean commitChanged() {
        return this.gitCommits.size() > 0;
    }
    public boolean resolved() {
        return this.gitConflicts == null && pullRequest.isConflict;
    }
    public boolean conflicts() {
        return this.gitConflicts != null && !pullRequest.isConflict;
    }
    public List<PullRequestCommit> getNewCommits() {
        return newCommits;
    }
    public String getConflictFilesToString() {
        if (gitConflicts == null) {
            return StringUtils.EMPTY;
        }
        return StringUtils.join(gitConflicts.conflictFiles, PullRequest.DELIMETER);
    }

    /**
     * 신규로 추가된 커밋목록을 반환한다.
     * @return
     */
    public List<PullRequestCommit> findNewCommits() {
        List<PullRequestCommit> currentCommits = new ArrayList<PullRequestCommit>();
        for (GitCommit commit: gitCommits) {
            boolean existCommit = false;
            for (PullRequestCommit pullRequestCommit: PullRequestCommit.find.where().eq("pullRequest", pullRequest).findList()) {
                if(commit.getId().equals(pullRequestCommit.commitId)) {
                    existCommit = true;
                    break;
                }
            }

            if (!existCommit) {
                PullRequestCommit pullRequestCommit = PullRequestCommit.bindPullRequestCommit(commit, pullRequest);
                currentCommits.add(pullRequestCommit);
            }
        }
        return currentCommits;
    }

    /**
     * 보낸코드의 병합결과를 저장한다.
     *
     * 신규 커밋 / 이전 커밋 / 병합상태(완료)
     */
    public void save() {
        pullRequest.endMerge();
        pullRequest.update();
    }

    public void saveCommits() {
        newCommits = findNewCommits();
        saveNewCommits();
        updatePriorCommits();
    }

    /**
     * 신규 커밋을 저장한다.
     *
     * @param pullRequest
     * @param commits
     * @return
     */
    public void saveNewCommits() {
        for (PullRequestCommit commit: newCommits) {
            commit.save();
        }
    }

    /**
     * 이전 커밋을 업데이트한다.
     *
     * DB의 커밋이 코드저장소의 커밋에 존재하지 않으면 이전커밋으로 판단하고 업데이트 한다.
     *
     * @param pullRequest
     * @param commits
     * @return
     */
    public void updatePriorCommits() {
        for (PullRequestCommit pullRequestCommit: PullRequestCommit.find.where().eq("pullRequest", pullRequest).findList()) {
            boolean existCommit = false;
            for (GitCommit commit: gitCommits) {
                if(commit.getId().equals(pullRequestCommit.commitId)) {
                    existCommit = true;
                    break;
                }
            }

            if (!existCommit) {
                pullRequestCommit.state = PullRequestCommit.State.PRIOR;
                pullRequestCommit.update();
            }
        }
    }

    /**
     * 보낸코드를 충돌 상태로 설정한다.
     */
    public void setConflictStateOfPullRequest() {
        pullRequest.isConflict = true;
        pullRequest.conflictFiles = getConflictFilesToString();
    }

    /**
     * 보낸코드를 충돌해결 상태로 설정한다.
     */
    public void setResolvedStateOfPullRequest() {
        pullRequest.isConflict = false;
        pullRequest.conflictFiles = StringUtils.EMPTY;
    }
}
