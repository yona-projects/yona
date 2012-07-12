package models;

import javax.persistence.*;

import play.data.validation.*;
import play.db.ebean.*;

import java.util.*;


/**
 * @author "Hwi Ahn"
 * 
 * @param id
 * @param name
 * @param overview Project explanation. Not mandatory
 * @param share_option 'True' means it can be shared. 'False' means it cannot be shared.
 * @param vcs Version Control System. At this moment, there are only two options: GIT and Subversion.
 * @param url Project site URL. It should be started with 'http://'.
 * @param owner The id of the owner of the project.
 * @param logoPath The file path for a logo
 */

@Entity
public class Project extends Model {
    private static final long serialVersionUID = 1L;
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
    public Long owner;
    public String logoPath;

    private static Finder<Long, Project> find = new Finder<Long, Project>(
            Long.class, Project.class);

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

    public static List<Project> findByOwner(Long owner) {
        return find.where().eq("owner", owner).findList();
    }
}
