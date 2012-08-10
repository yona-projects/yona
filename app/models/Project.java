package models;

import play.data.validation.Constraints;
import play.db.ebean.Model;
import javax.persistence.*;

import java.util.Iterator;
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
        newProject.url = "http://localhost:9000/" + newProject.name;
        newProject.save();
        ProjectUser.assignRole(User.SITE_MANAGER_ID, newProject.id, Role.SITEMANAGER);
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
    
    /**
     * 해당 유저가 속해있는 프로젝트들 중에서 해당 유저가 유일한 Manager인 프로젝트가 있는지 검사하고, 
     * 있다면 그 프로젝트들의 리스트를 반환합니다.
     * 
     * @param userId
     * @return
     */
    public static List<Project> isOnlyManager(Long userId) {
        List<Project> projects = find
                                    .select("id")
                                    .select("name")
                                    .where()
                                        .eq("projectUser.user.id", userId)
                                        .eq("projectUser.role.id", Role.MANAGER)
                                    .findList();
        
        Iterator<Project> iterator = projects.iterator();
        while(iterator.hasNext()){
            Project project = iterator.next();
            if(ProjectUser.isManager(project.id))
                projects.remove(project);
        }
        
        return projects;
    }
    
    /**
     * 해당 유저가 속해있는 프로젝트들의 리스트를 제공합니다.
     * 
     * @param ownerId
     * @return
     */
    public static List<Project> findProjectsByOwner(Long ownerId) {
        return find.where()
                .eq("projectUser.user.id", ownerId).findList();
    }
}
