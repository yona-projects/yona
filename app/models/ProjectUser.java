package models;

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
    public ProjectRole projectRole;

    private static Finder<Long, ProjectUser> find = new Finder<Long, ProjectUser>(
            Long.class, ProjectUser.class);

    public static ProjectRole findById(Long userId, Long projectId) {
        return find.where().eq("user.id", userId).eq("project.id", projectId)
                .findUnique().projectRole;
    }
}
