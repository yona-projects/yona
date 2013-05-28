package models;

import models.enumeration.State;
import org.joda.time.Duration;
import play.db.ebean.Model;
import utils.JodaDateUtil;

import javax.persistence.*;
import java.util.Date;
import java.util.List;

/**
 * @author Keesun Baik
 */
@Entity
public class PullRequest extends Model {

    private static final long serialVersionUID = 1L;

    public static Finder<Long, PullRequest> finder = new Finder<Long, PullRequest>(Long.class, PullRequest.class);

    @Id
    public Long id;

    public String title;

    @Lob
    public String body;

    @ManyToOne
    public Project toProject;

    @ManyToOne
    public Project fromProject;

    public String toBranch;

    public String fromBranch;

    @ManyToOne
    public User contributor;

    @ManyToOne
    public User receiver;

    @Temporal(TemporalType.TIMESTAMP)
    public Date created;

    @Temporal(TemporalType.TIMESTAMP)
    public Date updated;

    @Temporal(TemporalType.TIMESTAMP)
    public Date received;

    public State state = State.OPEN;

    @Override
    public String toString() {
        return "PullRequest{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", body='" + body + '\'' +
                ", toProject=" + toProject +
                ", fromProject=" + fromProject +
                ", toBranch='" + toBranch + '\'' +
                ", fromBranch='" + fromBranch + '\'' +
                ", contributor=" + contributor +
                ", receiver=" + receiver +
                ", created=" + created +
                ", updated=" + updated +
                ", received=" + received +
                ", state=" + state +
                '}';
    }

    public Duration createdAgo() {
        return JodaDateUtil.ago(this.created);
    }

    public Duration receivedAgo() {
        return JodaDateUtil.ago(this.received);
    }

    public boolean isOpen(){
        return this.state == State.OPEN;
    }

    public static PullRequest findById(long id) {
        return finder.byId(id);
    }

    public static PullRequest findDuplicatedPullRequest(PullRequest pullRequest) {
        return finder.where()
                .eq("fromBranch", pullRequest.fromBranch)
                .eq("toBranch", pullRequest.toBranch)
                .eq("fromProject", pullRequest.fromProject)
                .eq("toProject", pullRequest.toProject)
                .eq("state", State.OPEN)
                .findUnique();
    }

    public static List<PullRequest> findOpendPullRequests(Project project) {
        return finder.where()
                .eq("toProject", project)
                .eq("state", State.OPEN)
                .findList();
    }

    public static List<PullRequest> findClosedPullRequests(Project project) {
        return finder.where()
                .eq("toProject", project)
                .eq("state", State.CLOSED)
                .findList();
    }

    public static List<PullRequest> findSentPullRequests(Project project) {
        return finder.where()
                .eq("fromProject", project)
                .findList();
    }

    public static List<PullRequest> findRejectedPullRequests(Project project) {
        return finder.where()
                .eq("toProject", project)
                .eq("state", State.REJECTED)
                .findList();
    }
}
