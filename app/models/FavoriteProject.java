/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package models;

import io.ebean.Finder;
import io.ebean.Model;

import javax.annotation.Nonnull;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import java.util.List;

@Entity
public class FavoriteProject extends Model {
    public static Finder<Long, FavoriteProject> finder = new Finder<>(FavoriteProject.class);

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
        List<FavoriteProject> favoriteProjects = finder.query().where().eq("project.id", project.id).findList();

        for (FavoriteProject favoriteProject : favoriteProjects) {
            favoriteProject.project.refresh();
            favoriteProject.owner = project.owner;
            favoriteProject.projectName = project.name;
            favoriteProject.update();
        }
    }

    public static FavoriteProject findByProjectId(Long userId, Long projectId){
        return finder.query().where()
                .eq("user.id", userId)
                .eq("project.id", projectId)
                .findOne();
    }
}
