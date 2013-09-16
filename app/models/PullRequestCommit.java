package models;

import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import play.db.ebean.Model;

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
    
    public static String getStateByCommitId(String commitId) {
        return find.select("state").where().eq("commitId", commitId).findUnique().state.toString();
    }
    
    public enum State {
        PRIOR, CURRENT
    }
}
