/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package models;

import play.db.ebean.Model;

import javax.annotation.Nonnull;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import java.util.List;

@Entity
public class FavoriteProject extends Model {
    public static Finder<Long, FavoriteProject> finder = new Finder<>(Long.class, FavoriteProject.class);

    @Id
    public Long id;

    @ManyToOne
    public User user;

    @OneToOne
    public Project project;

    public String owner;
    public String projectName;

    public FavoriteProject(User user, Project project) {
        this.user = user;
        this.project = project;

        this.owner = project.owner;
        this.projectName = project.name;
    }

    public static void updateFavoriteProject(@Nonnull Project project){
        List<FavoriteProject> favoriteProjects = finder.where().eq("project.id", project.id).findList();

        for (FavoriteProject favoriteProject : favoriteProjects) {
            favoriteProject.project.refresh();
            favoriteProject.owner = project.owner;
            favoriteProject.projectName = project.name;
            favoriteProject.update();
        }
    }

    public static FavoriteProject findByProjectId(Long userId, Long projectId){
        return finder.where()
                .eq("user.id", userId)
                .eq("project.id", projectId)
                .findUnique();
    }
}
