package models;

import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import play.db.ebean.Model;

@Entity
public class PullRequestCommit extends Model implements TimelineItem {
    private static final long serialVersionUID = 1L;

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
    public String state;
    
    public String getCommitId() {
        return commitId;
    }
    
    public String getCommitShortId() {
        return commitShortId;
    }

    @Override
    public Date getDate() {
        return created;
    }
}
