/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Wansoon Park
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package models;

import models.enumeration.State;
import playRepository.GitCommit;

import java.util.ArrayList;
import java.util.List;

public class PullRequestMergeResult {
    private List<GitCommit> gitCommits;
    private List<PullRequestCommit> newCommits;
    private PullRequest pullRequest;

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
    public boolean hasDiffCommits() {
        return this.gitCommits.size() > 0;
    }

    public boolean conflicts() {
        return pullRequest.isConflict;
    }

    public List<PullRequestCommit> getNewCommits() {
        return newCommits;
    }

    public List<PullRequestCommit> findNewCommits() {
        List<PullRequestCommit> currentCommits = new ArrayList<>();
        for (GitCommit commit: gitCommits) {
            boolean existCommit = false;
            List<PullRequestCommit> pullRequestCommits = PullRequestCommit.find.where()
                                    .eq("pullRequest", pullRequest)
                                    .eq("state", PullRequestCommit.State.CURRENT)
                                    .findList();

            for (PullRequestCommit pullRequestCommit: pullRequestCommits) {
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
     * save merge result
     *
     * new commit/ previous commit / merge state(completion)
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

    public void saveNewCommits() {
        for (PullRequestCommit commit: newCommits) {
            commit.save();
        }
    }

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

    public void setConflictStateOfPullRequest() {
        pullRequest.isConflict = true;
    }

    public void setResolvedStateOfPullRequest() {
        pullRequest.isConflict = false;
    }

    public void setMergedStateOfPullRequest(User receiver) {
        pullRequest.isConflict = false;
        pullRequest.state = State.MERGED;
        pullRequest.receiver = receiver;
    }
}
