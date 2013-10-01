package models;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import playRepository.GitConflicts;

/**
 * 보낸코드 병합 결과
 */
public class PullRequestMergeResult {
    private GitConflicts gitConflicts;
    private List<PullRequestCommit> commits;
    private PullRequest pullRequest;
    
    public GitConflicts getGitConflicts() {
        return gitConflicts;
    }
    public void setGitConflicts(GitConflicts gitConflicts) {
        this.gitConflicts = gitConflicts;
    }
    public List<PullRequestCommit> getCommits() {
        return commits;
    }
    public void setCommits(List<PullRequestCommit> commits) {
        this.commits = commits;
    }
    public PullRequest getPullRequest() {
        return pullRequest;
    }
    public void setPullRequest(PullRequest pullRequest) {
        this.pullRequest = pullRequest;
    }
    public boolean commitChanged() {
        return this.commits.size() > 0;
    }
    public boolean resolved() {
        return this.gitConflicts == null && pullRequest.isConflict;
    }
    public boolean conflicts() {
        return this.gitConflicts != null && !pullRequest.isConflict;
    }
    
    public String getConflictFilesToString() {
        if (gitConflicts == null) {
            return StringUtils.EMPTY;
        }
        return StringUtils.join(gitConflicts.conflictFiles, PullRequest.DELIMETER);
    }
}
