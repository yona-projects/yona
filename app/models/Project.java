package models;

/*
 * @author: Hwi Ahn
 */

import javax.persistence.*;

import play.data.validation.*;
import play.db.ebean.*;

import java.util.*;

@Entity
public class Project extends Model {

    @Id
    public Long id;
    @Constraints.Required
    @Constraints.Pattern("^[a-zA-Z0-9_]*$")
    public String name;
    public String overview; // Project explanation. Not mandatory
    public boolean share_option; // 'True' means it can be shared. 'False' means
                                 // it cannot be shared.
    public String vcs; // Version Control System. At this moment, there are only
                       // two options: GIT and Subversion.
    public String url; // Project site URL. It should be started with 'http://'.
    public Long owner; // The id of the owner of the project.

    public Project() {
        this.owner = new Long(1);
    }

    public static final String defaultSiteURL = "http://localhost:9000";
    public static Finder<Long, Project> find = new Finder(Long.class,
            Project.class);

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

    public static void delete(Project deletedProject) {
        deletedProject.delete();
    }

    public static Project findById(Long id) {
        return find.byId(id);
    }

    public static List<Project> findByOwner(Long owner) {
        return find.where().eq("owner", owner).findList();
    }

}
