package models;

import models.enumeration.ResourceType;
import models.enumeration.State;
import models.resource.Resource;
import org.joda.time.Duration;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import utils.JodaDateUtil;

import javax.persistence.*;
import javax.validation.constraints.Size;
import java.util.Date;
import java.util.List;

/**
 * @author Keesun Baik
 */
@Entity
public class PullRequest extends Model {

    private static final long serialVersionUID = 1L;

    public static final Finder<Long, PullRequest> finder = new Finder<>(Long.class, PullRequest.class);

    @Id
    public Long id;

    @Constraints.Required
    @Size(max=255)
    public String title;

    @Lob
    public String body;

    @ManyToOne
    public Project toProject;

    @ManyToOne
    public Project fromProject;

    @Constraints.Required
    @Size(max=255)
    public String toBranch;

    @Constraints.Required
    @Size(max=255)
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

    public boolean isOpen() {
        return this.state == State.OPEN;
    }

    public boolean isRejected() {
        return this.state == State.REJECTED;
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
                .order().desc("created")
                .findList();
    }

    public static List<PullRequest> findOpendPullRequestsByDaysAgo(Project project, int days) {
        return finder.where()
                .eq("toProject", project)
                .eq("state", State.OPEN)
                .ge("created", JodaDateUtil.before(days))
                .order().desc("created")
                .findList();
    }

    public static List<PullRequest> findClosedPullRequests(Project project) {
        return finder.where()
                .eq("toProject", project)
                .eq("state", State.CLOSED)
                .order().desc("created")
                .findList();
    }

    public static List<PullRequest> findSentPullRequests(Project project) {
        return finder.where()
                .eq("fromProject", project)
                .order().desc("created")
                .findList();
    }

    public static List<PullRequest> findAcceptedPullRequests(Project project) {
        return finder.where()
                .eq("fromProject", project)
                .eq("state", State.CLOSED)
                .order().desc("created")
                .findList();
    }

    public static List<PullRequest> findRejectedPullRequests(Project project) {
        return finder.where()
                .eq("toProject", project)
                .eq("state", State.REJECTED)
                .order().desc("created")
                .findList();
    }

    public static List<PullRequest> allReceivedRequests(Project project) {
        return finder.where()
                .eq("toProject", project)
                .order().desc("created")
                .findList();
    }

    public static List<PullRequest> findRecentlyReceived(Project project, int size) {
        return finder.where()
                .eq("toProject", project)
                .order().desc("created")
                .findPagingList(size).getPage(0)
                .getList();
    }

    public static int countOpenedPullRequests(Project project) {
        return finder.where()
                .eq("toProject", project)
                .eq("state", State.OPEN)
                .findRowCount();
    }

    public Resource asResource() {
        return new Resource() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public Project getProject() {
                return toProject;
            }

            @Override
            public ResourceType getType() {
                return ResourceType.PULL_REQUEST;
            }

            @Override
            public Long getAuthorId() {
                return contributor.id;
            }
        };
    }

}
