package models;

import play.db.ebean.Model;
import play.db.ebean.Transactional;

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
    public static int MAX_RECENT_LIST_PER_USER = 5;

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
                .eq("userId", user.id).findList();

        List<Project> found = new ArrayList<>();

        for(RecentProject rp: recentProjects){
            found.add(0, Project.findByOwnerAndProjectName(rp.owner, rp.projectName));
        }

        return found;
    }

    @Transactional
    public static void addNew(User user, Project project){
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
        if(recentProjects.size() > MAX_RECENT_LIST_PER_USER){
            Comparator<RecentProject> comparator = new Comparator<RecentProject>() {
                @Override
                public int compare(RecentProject p1, RecentProject p2) {
                    return Long.compare( p1.id, p2.id);
                }
            };
            RecentProject oldest = Collections.min(recentProjects, comparator);
            oldest.delete();
        }
    }
}
