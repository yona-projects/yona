package models;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import play.db.ebean.Model;

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

    public static Role findRolebyIds(Long userId, Long projectId) {
        return find.where().eq("user.id", userId).eq("project.id", projectId)
                .findUnique().role;
    }
    
    public static void create(Long userId, Long projectId, Long roleId) {
        ProjectUser projectUser = new ProjectUser(userId, projectId, roleId);
        projectUser.save();
    }
}
