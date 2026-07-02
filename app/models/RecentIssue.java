package models;

import io.ebean.Finder;
import io.ebean.Model;
import io.ebean.annotation.Transactional;

import javax.annotation.Nonnull;
import jakarta.persistence.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import controllers.routes;

@Entity
public class RecentIssue extends Model {
    private static final long serialVersionUID = 2888713013271878179L;
    public static int MAX_RECENT_LIST_PER_USER = 100;

    public static Finder<Long, RecentIssue> find = new Finder<>(RecentIssue.class);

    @Id
    public Long id;

    public Long userId;
    public Long issueId;
    public Long postingId;
    public String title;
    public String url = "";
    public Date createdDate;

    public RecentIssue(User user, String title, Issue issue, Posting posting) {
        userId = user.id;
        this.title = title;
        if (issue != null) this.issueId = issue.id;
        if (posting != null) this.postingId = posting.id;
        if (issue != null) {
            this.url = controllers.routes.IssueApp.issue(issue.project.owner, issue.project.name, issue.getNumber()).url();
        } else if (posting != null) {
            this.url = controllers.routes.BoardApp.post(posting.project.owner, posting.project.name, posting.getNumber()).url();
        }
        this.createdDate = new Date();
    }

    public static List<RecentIssue> getRecentIssues(@Nonnull User user){
        return find.query().where()
                .eq("userId", user.id).orderBy("id desc").findList();
    }

    public static void addNewIssue(final User user, final Issue issue){
        CompletableFuture.runAsync(() -> addVisitIssueHistory(user, issue));
    }

    public static void addNewPosting(final User user, final Posting posting){
        CompletableFuture.runAsync(() -> addVisitPostingHistory(user, posting));
    }


    @Transactional
    private static void addVisitIssueHistory(User user, Issue issue){
        try {
            deletePreviousIssue(user, issue.id);

            RecentIssue recentIssue = new RecentIssue(user, issue.title, issue, null);
            recentIssue.save();

            deleteOldestIfOverflow(user);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    @Transactional
    private static void addVisitPostingHistory(User user, Posting posting){
        try {
            deletePreviousPosting(user, posting.id);

            RecentIssue recentIssue = new RecentIssue(user, posting.title, null, posting);
            recentIssue.save();

            deleteOldestIfOverflow(user);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public static void deletePreviousIssue(User user, Long issueId) {
        try {
            RecentIssue existed = find.query().where()
                    .eq("userId", user.id)
                    .eq("issueId", issueId).findOne();
            play.Logger.debug("deletePreviousIssue {}", existed);

            if(existed != null){
                existed.delete();
            }
        } catch (Exception e) {
            play.Logger.debug(e.getMessage());
            e.printStackTrace();
        }

    }

    public static void deletePreviousPosting(User user, Long postingId) {
        RecentIssue existed = find.query().where()
                .eq("userId", user.id)
                .eq("postingId", postingId).findOne();

        if(existed != null){
            existed.delete();
        }
    }

    private static void deleteOldestIfOverflow(User user) {
        List<RecentIssue> recentProjects = find.query().where()
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
        List<RecentIssue> recentIssues = find.query().where()
                .eq("userId", user.id).findList();
        for (RecentIssue ri : recentIssues) {
            ri.delete();
        }
    }

    public String getNumber(){
        String[] paths = this.url.split("/");
        if (paths.length <5) return url;
        return paths[2] + " #" + paths[4];
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
