/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package models;

import models.enumeration.State;
import play.db.ebean.Model;

import javax.annotation.Nonnull;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import java.util.List;

@Entity
public class FavoriteIssue extends Model {
    public static Finder<Long, FavoriteIssue> find = new Finder<>(Long.class, FavoriteIssue.class);

    @Id
    public Long id;

    @ManyToOne
    public User user;

    @OneToOne
    public Issue issue;

    public FavoriteIssue(User user, Issue issue) {
        this.user = user;
        this.issue = issue;
    }

    public static void updateFavoriteIssue(@Nonnull Issue issue){
        List<FavoriteIssue> favoriteIssues = find.where().eq("issue.id", issue.id).findList();

        for (FavoriteIssue favoriteProject : favoriteIssues) {
            favoriteProject.issue.refresh();
            favoriteProject.update();
        }
    }

    public static FavoriteIssue findByIssueId(Long userId, Long issueId){
        return find.where()
                .eq("user.id", userId)
                .eq("issue.id", issueId)
                .findUnique();
    }

    public static int getNumberOpenFavoriteIssues(Long userId){
        return find.where()
                .eq("user.id", userId)
                .eq("issue.state", State.OPEN)
                .findRowCount();
    }
}
