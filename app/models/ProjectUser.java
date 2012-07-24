package models;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import play.db.ebean.Model;

/**
 * @author "Hwi Ahn"
 *
 */
@Entity
public class ProjectUser extends Model {
    private static final long serialVersionUID = 1L;

    @Id
    public Long id;
    @ManyToOne
    public User user;
    @ManyToOne
    public Project project;
    @ManyToOne
    public Role role;

    public ProjectUser(Long userId, Long projectId, Long roleId) {
        this.user = User.findById(userId);
        this.project = Project.findById(projectId);
        this.role = Role.findById(roleId);
    }

    private static Finder<Long, ProjectUser> find = new Finder<Long, ProjectUser>(
            Long.class, ProjectUser.class);

    public static ProjectUser findByIds(Long userId, Long projectId) {
        return find.where().eq("user.id", userId).eq("project.id", projectId)
                .findUnique();
    }

    public static void create(Long userId, Long projectId, Long roleId) {
        ProjectUser projectUser = new ProjectUser(userId, projectId, roleId);
        projectUser.save();
    }

    public static void update(Long userId, Long projectId, Long roleId) {
        ProjectUser projectUser = new ProjectUser(userId, projectId, roleId);
        projectUser.update(ProjectUser.findByIds(userId, projectId).id);
    }

    public static List<Project> findProjectsByOwner(Long ownerId) {
        List<ProjectUser> projectUsers = find.where().eq("user.id", ownerId)
                .findList();
        List<Project> projects = new ArrayList<Project>();
        for (ProjectUser projectUser : projectUsers) {
            projects.add(Project.findById(projectUser.project.id));
        }
        return projects;
    }

    public static List<User> findUsersByProject(Long projectId) {
        List<ProjectUser> projectUsers = find.where()
                .eq("project.id", projectId).findList();
        List<User> users = new ArrayList<User>();
        for (ProjectUser projectUser : projectUsers) {
            if (projectUser.role.id.equals(1l))
                users.add(0, User.findById(projectUser.user.id));
            else
                users.add(User.findById(projectUser.user.id));
        }
        return users;
    }

    public static Role findRoleByIds(Long userId, Long projectId) {
        Long roleId = find.where().eq("user.id", userId)
                .eq("project.id", projectId).findUnique().role.id;
        return Role.findById(roleId);
    }

}
