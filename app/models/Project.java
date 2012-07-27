package models;

import play.data.validation.Constraints;
import play.db.ebean.Model;

import javax.persistence.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @param id
 * @param name
 * @param overview
 *            Project explanation. Not mandatory
 * @param share_option
 *            'True' means it can be shared. 'False' means it cannot be shared.
 * @param vcs
 *            Version Control System. At this moment, there are only two
 *            options: GIT and Subversion.
 * @param url
 *            Project site URL. It should be started with 'http://'.
 * @param owner
 *            The id of the owner of the project.
 * @param logoPath
 *            The file path for a logo
 * @author "Hwi Ahn"
 */

@Entity
public class Project extends Model {
    private static final long serialVersionUID = 1L;
    private static Finder<Long, Project> find = new Finder<Long, Project>(
            Long.class, Project.class);
    public static final String defaultSiteURL = "http://localhost:9000";

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

    public static Long create(Project newProject) {
        newProject.save();
        newProject.url = defaultSiteURL + "/project/"
                + Long.toString(newProject.id); // Setting a default URL.
        newProject.update();
        return newProject.id;
    }

    public static Long update(Project updatedProject, Long id) {
        updatedProject.update(id);
        return id;
    }

    public static void delete(Long id) {

        Project.findById(id).delete();
    }

    public static Project findById(Long id) {
        return find.byId(id);
    }

    public void add(Issue issue) {
        if (this.issues == null) {
            this.issues = new HashSet<Issue>();
        }

        this.issues.add(issue);
        //issue.project = this;
    }

    public static Project findByName(String name) {
        return find.where().eq("name", name).findUnique();
    }
}
