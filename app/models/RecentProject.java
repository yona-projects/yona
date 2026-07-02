package models;

import io.ebean.Finder;
import io.ebean.Model;
import io.ebean.annotation.Transactional;

import javax.annotation.Nonnull;
import jakarta.persistence.*;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "project_id"}))
public class RecentProject extends Model {
    private static final long serialVersionUID = 7306890271871188281L;
    public static int MAX_RECENT_LIST_PER_USER = 30;

    public static Finder<Long, RecentProject> find = new Finder<>(RecentProject.class);

    @Id
    public Long id;

    public Long userId;
    public String owner;
    public Long projectId;
    public String projectName;

    public RecentProject(User user, Project project) {
        userId = user.id;
        this.owner = project.owner;
        this.projectId = project.id;
        this.projectName = project.name;
    }

    public static List<Project> getRecentProjects(@Nonnull User user){
        List<RecentProject> recentProjects = find.query().where()
                .eq("userId", user.id).orderBy("id desc").findList();

        List<Project> found = new ArrayList<>();

        // remove deleted projects
        for(RecentProject rp: recentProjects){
            Project project = Project.find.byId(rp.projectId);
            if(project != null){
                found.add(project);
            }
        }

        return found;
    }

    public static void addNew(final User user, final Project project){
        CompletableFuture.runAsync(() -> addVisitHistory(user, project));
    }

    @Transactional
    private static void addVisitHistory(User user, Project project){
        try {
            deletePrevious(user, project);


            RecentProject recentProject = new RecentProject(user, project);
            recentProject.save();
            play.Logger.debug("recentProject {}", recentProject);

            deleteOldestIfOverflow(user);
        } catch (OptimisticLockException ole){
            ole.printStackTrace();
        }
    }

    public static void deletePrevious(User user, Project project) {
        RecentProject existed = find.query().where()
                .eq("userId", user.id)
                .eq("projectId", project.id).findOne();

        if(existed != null){
            existed.delete();
        }
    }

    private static void deleteOldestIfOverflow(User user) {
        List<RecentProject> recentProjects = find.query().where()
                .eq("userId", user.id).findList();
        while(recentProjects.size() > MAX_RECENT_LIST_PER_USER){
            Comparator<RecentProject> comparator = new Comparator<RecentProject>() {
                @Override
                public int compare(RecentProject p1, RecentProject p2) {
                    return Long.compare( p1.id, p2.id);
                }
            };
            RecentProject oldest = Collections.min(recentProjects, comparator);
            oldest.refresh();
            oldest.delete();
        }
    }

    public static void deleteAll(User user) {
        List<RecentProject> recentProjects = find.query().where()
                .eq("userId", user.id).findList();
        for (RecentProject rp : recentProjects) {
            rp.delete();
        }
    }

    @Override
    public String toString() {
        return "RecentProject{" +
                "id=" + id +
                ", userId=" + userId +
                ", owner='" + owner + '\'' +
                ", projectId=" + projectId +
                ", projectName='" + projectName + '\'' +
                '}';
    }
}
