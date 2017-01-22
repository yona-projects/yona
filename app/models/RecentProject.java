package models;

import play.db.ebean.Model;
import play.db.ebean.Transactional;
import play.libs.F;

import javax.annotation.Nonnull;
import javax.persistence.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "project_id"}))
public class RecentProject extends Model {
    private static final long serialVersionUID = 7306890271871188281L;
    public static int MAX_RECENT_LIST_PER_USER = 20;

    public static Finder<Long, RecentProject> find = new Finder<>(Long.class, RecentProject.class);

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

    @Transactional
    public static List<Project> getRecentProjects(@Nonnull User user){
        List<RecentProject> recentProjects = find.where()
                .eq("userId", user.id).orderBy("id desc").findList();

        List<Project> found = new ArrayList<>();

        // remove deleted projects
        for(RecentProject rp: recentProjects){
            Project byOwnerAndProjectName = Project.findByOwnerAndProjectName(rp.owner, rp.projectName);
            if(byOwnerAndProjectName != null){
                found.add(byOwnerAndProjectName);
            }
        }

        return found;
    }

    public static void addNew(final User user, final Project project){
        F.Promise<Void> promise = F.Promise.promise(
                new F.Function0<Void>() {
                    public Void apply() {
                        addVisitHistory(user, project);
                        return null;
                    }
                }
        );
    }

    @Transactional
    private static void addVisitHistory(User user, Project project){
        try {
            deletePrevious(user, project);

            RecentProject recentProject = new RecentProject(user, project);
            recentProject.save();

            deleteOldestIfOverflow(user);
        } catch (OptimisticLockException ole){
            ole.printStackTrace();
        }
    }

    private static void deletePrevious(User user, Project project) {
        RecentProject existed = find.where()
                .eq("userId", user.id)
                .eq("projectId", project.id).findUnique();

        if(existed != null){
            existed.delete();
        }

    }

    private static void deleteOldestIfOverflow(User user) {
        List<RecentProject> recentProjects = find.where()
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
}
