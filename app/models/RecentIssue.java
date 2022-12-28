package models;

import javafx.geometry.Pos;
import play.db.ebean.Model;
import play.db.ebean.Transactional;
import play.libs.F;

import javax.annotation.Nonnull;
import javax.persistence.*;
import java.util.*;
import controllers.routes;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "project_id"}))
public class RecentIssue extends Model {
    private static final long serialVersionUID = 2888713013271878179L;
    public static int MAX_RECENT_LIST_PER_USER = 50;

    public static Finder<Long, RecentIssue> find = new Finder<>(Long.class, RecentIssue.class);

    @Id
    public Long id;

    public Long userId;
    public Long issueId;
    public Long postingId;
    public String title;
    public String url;
    public Date createdDate;

    public RecentIssue(User user, String title, Issue issue, Posting posting) {
        userId = user.id;
        this.title = title;
        this.issueId = issue.id;
        this.postingId = posting.id;
        if (issue != null) {
            this.url = controllers.routes.IssueApp.issue(issue.project.owner, issue.project.name, issue.getNumber()).url();
        } else if (posting != null) {
            this.url = controllers.routes.BoardApp.post(posting.project.owner, posting.project.name, posting.getNumber()).url();
        }
        this.createdDate = new Date();
    }

    public static List<AbstractPosting> getRecentIssues(@Nonnull User user){
        List<RecentIssue> recentIssues = find.where()
                .eq("userId", user.id).orderBy("id desc").findList();

        List<AbstractPosting> found = new ArrayList<>();

        // remove deleted projects
        for(RecentIssue ri: recentIssues){
            if (ri.postingId != null) {
                found.add(Posting.finder.byId(ri.id));
            } else if (ri.issueId != null){
                found.add(Issue.finder.byId(ri.issueId));
            }
        }

        return found;
    }

    public static void addNewIssue(final User user, final Issue issue){
        F.Promise<Void> promise = F.Promise.promise(
                new F.Function0<Void>() {
                    public Void apply() {
                        play.Logger.debug("apply --> visit issue {}", issue.getNumber());
                        addVisitIssueHistory(user, issue);
                        return null;
                    }
                }
        );
    }

    public static void addNewPosting(final User user, final Posting posting){
        F.Promise<Void> promise = F.Promise.promise(
                new F.Function0<Void>() {
                    public Void apply() {
                        addVisitPostingHistory(user, posting);
                        return null;
                    }
                }
        );
    }


    @Transactional
    private static void addVisitIssueHistory(User user, Issue issue){
        try {
            deletePreviousIssue(user, issue.id);

            RecentIssue recentIssue = new RecentIssue(user, issue.title, issue, null);
            recentIssue.save();
            play.Logger.debug("recentIssue {}", recentIssue);

            deleteOldestIfOverflow(user);
        } catch (OptimisticLockException ole){
            ole.printStackTrace();
        }
    }

    @Transactional
    private static void addVisitPostingHistory(User user, Posting posting){
        try {
            deletePreviousPosting(user, posting.id);

            RecentIssue recentProject = new RecentIssue(user, posting.title, null, posting);
            recentProject.save();

            deleteOldestIfOverflow(user);
        } catch (OptimisticLockException ole){
            ole.printStackTrace();
        }
    }

    public static void deletePreviousIssue(User user, Long issueId) {
        RecentIssue existed = find.where()
                .eq("userId", user.id)
                .eq("issueId", issueId).findUnique();

        if(existed != null){
            existed.delete();
        }
    }

    public static void deletePreviousPosting(User user, Long postingId) {
        RecentIssue existed = find.where()
                .eq("userId", user.id)
                .eq("postingId", postingId).findUnique();

        if(existed != null){
            existed.delete();
        }
    }

    private static void deleteOldestIfOverflow(User user) {
        List<RecentIssue> recentProjects = find.where()
                .eq("userId", user.id).findList();
        while(recentProjects.size() > MAX_RECENT_LIST_PER_USER){
            Comparator<RecentIssue> comparator = new Comparator<RecentIssue>() {
                @Override
                public int compare(RecentIssue p1, RecentIssue p2) {
                    return Long.compare( p1.id, p2.id);
                }
            };
            RecentIssue oldest = Collections.min(recentProjects, comparator);
            oldest.refresh();
            oldest.delete();
        }
    }

    public static void deleteAll(User user) {
        List<RecentIssue> recentIssues = find.where()
                .eq("userId", user.id).findList();
        for (RecentIssue ri : recentIssues) {
            ri.delete();
        }
    }

    @Override
    public String toString() {
        return "RecentIssue{" +
                "id=" + id +
                ", userId=" + userId +
                ", issueId=" + issueId +
                ", postingId=" + postingId +
                ", title='" + title + '\'' +
                ", url='" + url + '\'' +
                ", createdDate=" + createdDate +
                '}';
    }
}
