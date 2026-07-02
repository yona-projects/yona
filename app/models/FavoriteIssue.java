/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package models;

import models.enumeration.State;
import io.ebean.Finder;
import io.ebean.Model;

import javax.annotation.Nonnull;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import java.util.List;

@Entity
public class FavoriteIssue extends Model {
    public static Finder<Long, FavoriteIssue> find = new Finder<>(FavoriteIssue.class);

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
        List<FavoriteIssue> favoriteIssues = find.query().where().eq("issue.id", issue.id).findList();

        for (FavoriteIssue favoriteProject : favoriteIssues) {
            favoriteProject.issue.refresh();
            favoriteProject.update();
        }
    }

    public static FavoriteIssue findByIssueId(Long userId, Long issueId){
        return find.query().where()
                .eq("user.id", userId)
                .eq("issue.id", issueId)
                .findOne();
    }

    public static int getNumberOpenFavoriteIssues(Long userId){
        return find.query().where()
                .eq("user.id", userId)
                .eq("issue.state", State.OPEN)
                .findCount();
    }
}
