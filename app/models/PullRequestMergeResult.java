package models;

import java.util.List;

import models.enumeration.State;

import playRepository.GitConflicts;

public class PullRequestMergeResult {
    private GitConflicts conflicts;
    private List<PullRequestCommit> commits;
    private PullRequest pullRequest;
    
    public GitConflicts getConflicts() {
        return conflicts;
    }
    public void setConflicts(GitConflicts conflicts) {
        this.conflicts = conflicts;
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
    
}
