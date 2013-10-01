package models;

import java.util.Date;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;

import play.db.ebean.Model;
import playRepository.GitCommit;
import utils.JodaDateUtil;

/**
 * 
 * 보낸코드 커밋정보 저장
 */
@Entity
public class PullRequestCommit extends Model implements TimelineItem {

    private static final long serialVersionUID = -4343181252386722689L;

    public static Finder<Long, PullRequestCommit> find = new Finder<>(Long.class, PullRequestCommit.class);
    
    @Id    
    public String id;
    
    @ManyToOne
    public PullRequest pullRequest;
    
    public String commitId;
    public Date authorDate;
    public Date created;
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
    
    public static State getStateByCommitId(PullRequest pullRequest, String commitId) {
        return find.select("state").where().eq("pullRequest", pullRequest).eq("commitId", commitId).findUnique().state;
    }
    
    public static PullRequestCommit findById(String id) {
        return find.byId(Long.parseLong(id));
    }
    
    public static List<PullRequestCommit> getCurrentCommits(PullRequest pullRequest) {
        return find.where().eq("pullRequest", pullRequest).eq("state", State.CURRENT).findList();
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
