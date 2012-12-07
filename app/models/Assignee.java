package models;

import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

import org.apache.commons.lang.builder.HashCodeBuilder;

import play.Logger;
import play.data.validation.Constraints.Required;
import play.db.ebean.Model;
import play.db.ebean.Model.Finder;

@Entity
public class Assignee extends Model {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    @Id
    public Long id;

    @ManyToOne
    @Required
    public User user;

    @ManyToOne
    @Required
    public Project project;

    @OneToMany(mappedBy = "assignee", cascade = CascadeType.ALL)
    public Set<Issue> issues;

    public static Finder<Long, Assignee> finder = new Finder<Long, Assignee>(Long.class,
            Assignee.class);

    public Assignee(Long userId, Long projectId) {
        user = User.find.byId(userId);
        project = Project.find.byId(projectId);
    }

    static public Assignee get(Long userId, Long projectId) {
        Assignee assignee = finder.where().eq("user.id", userId).eq("project.id", projectId)
                .findUnique();
        if (assignee == null) {
            assignee = new Assignee(userId, projectId);
        }
        return assignee;
    }

    public static Assignee add(Long userId, Long projectId) {
        Assignee assignee = finder.where()
                .eq("user.id", userId).eq("project.id", projectId).findUnique();
        if (assignee != null) {
            return assignee;
        } else {
            return new Assignee(userId, projectId);
        }
    }
}