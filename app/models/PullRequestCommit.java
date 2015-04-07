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

import org.apache.commons.lang3.StringUtils;
import play.db.ebean.Model;
import playRepository.GitCommit;
import utils.JodaDateUtil;

import javax.annotation.Nonnull;
import javax.persistence.*;
import java.util.Date;
import java.util.List;

@Entity
public class PullRequestCommit extends Model implements TimelineItem {

    private static final long serialVersionUID = -4343181252386722689L;

    public static final Finder<Long, PullRequestCommit> find = new Finder<>(Long.class, PullRequestCommit.class);

    @Id
    public String id;

    @ManyToOne
    public PullRequest pullRequest;

    public String commitId;
    public Date authorDate;
    public Date created;
    @Lob
    public String commitMessage;
    public String commitShortId;
    public String authorEmail;

    @Enumerated(EnumType.STRING)
    public State state;

    public String getAuthorEmail() {
        return authorEmail;
    }

    public Date getAuthorDate() {
        return authorDate;
    }

    public String getCommitMessage() {
        return commitMessage;
    }

    public @Nonnull String getCommitShortMessage() {
        String commitMessage = getCommitMessage();
        if (StringUtils.isEmpty(commitMessage)) {
            return "";
        }

        if (!commitMessage.contains("\n")) {
            return commitMessage;
        }

        String[] segments = commitMessage.split("\n");
        if (segments.length > 0) {
            return segments[0];
        }

        return "";
    }

    public String getCommitId() {
        return commitId;
    }

    public String getCommitShortId() {
        return commitShortId;
    }

    @Transient
    @Override
    public Date getDate() {
        return created;
    }

    /**
     * PullRequestCommits that have the same {@code pullRequest} and {@code commitId}
     * but have different state. And, this method find the latest PullRequestCommit.
     * 
     * @param pullRequest
     * @param commitId
     * @return
     */
    public static PullRequestCommit getByCommitId(PullRequest pullRequest, String commitId) {
        return find.select("state").where().eq("pullRequest", pullRequest)
                .eq("commitId",commitId)
                .orderBy().desc("created")
                .setMaxRows(1)
                .findUnique();
    }

    public static State getStateByCommitId(PullRequest pullRequest, String commitId) {
        return getByCommitId(pullRequest, commitId).state;
    }

    public static PullRequestCommit findById(String id) {
        return find.byId(Long.parseLong(id));
    }

    public static List<PullRequestCommit> getCurrentCommits(PullRequest pullRequest) {
        return find.where()
                .eq("pullRequest", pullRequest)
                .eq("state", State.CURRENT)
                .order().desc("created")
                .findList();
    }

    public static List<PullRequestCommit> getPriorCommits(PullRequest pullRequest) {
        return find.where().eq("pullRequest", pullRequest).eq("state", State.PRIOR).findList();
    }

    public static PullRequestCommit bindPullRequestCommit(GitCommit commit, PullRequest pullRequest) {
        PullRequestCommit pullRequestCommit = new PullRequestCommit();
        pullRequestCommit.commitId = commit.getId();
        pullRequestCommit.commitShortId = commit.getShortId();
        pullRequestCommit.commitMessage = commit.getMessage();
        pullRequestCommit.authorEmail = commit.getAuthorEmail();
        pullRequestCommit.authorDate = commit.getAuthorDate();
        pullRequestCommit.created = JodaDateUtil.now();
        pullRequestCommit.state = PullRequestCommit.State.CURRENT;
        pullRequestCommit.pullRequest = pullRequest;

        return pullRequestCommit;
    }
    public enum State {
        PRIOR, CURRENT
    }
}
