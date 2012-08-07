package models;

import play.data.validation.Constraints;
import play.db.ebean.Model;
import utils.Constants;

import javax.persistence.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 
 * @author "Hwi Ahn"
 */

@Entity
public class Project extends Model {
    private static final long serialVersionUID = 1L;
    private static Finder<Long, Project> find = new Finder<Long, Project>(
            Long.class, Project.class);

    @Id
    public Long id;
    @Constraints.Required
    @Constraints.Pattern("^[a-zA-Z0-9_]*$")
    public String name;
    public String overview;
    public boolean share_option;
    public String vcs;
    public String url;
    public String logoPath;
    
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    public Set<Issue> issues;
    
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    public Set<ProjectUser> projectUser;
    
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    public Set<Post> posts;
    
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    public Set<Milestone> milestones;

    public static Long create(Project newProject) {
        newProject.save();
        newProject.url = Constants.DEFAULT_SITE_URL + "/"
                + newProject.name; // Setting a default URL.
        newProject.update();
        return newProject.id;
    }

    public static String update(Project updatedProject, String projectName) {
        updatedProject.update(Project.findByName(projectName).id);
        return updatedProject.name;
    }

    public static void delete(Long id) {
        Project.findById(id).delete();
    }

    public static Project findById(Long id) {
        return find.byId(id);
    }

    public static Project findByName(String name) {
        return find.where().eq("name", name).findUnique();
    }

    public void add(Issue issue) {
        if (this.issues == null) {
            this.issues = new HashSet<Issue>();
        }

        this.issues.add(issue);
        //issue.project = this;
    }
}
