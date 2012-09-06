package models;

import models.support.*;
import play.data.validation.*;
import play.db.ebean.*;

import javax.persistence.*;
import java.util.*;

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
    public String vcs;
    public String url;
    public String logoPath;
    public String owner;

    public boolean share_option;
    public boolean isAuthorEditable;
    
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    public List<Issue> issues;
    
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    public List<ProjectUser> projectUser;
    
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    public List<Post> posts;
    
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    public List<Milestone> milestones;

    public static Long create(Project newProject) {
        newProject.url = "http://localhost:9000/" + newProject.name;
        newProject.save();
        ProjectUser.assignRole(User.SITE_MANAGER_ID, newProject.id, Role.SITEMANAGER);
        return newProject.id;
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
    
    public static Project findByNameAndOwner(String userName, String projectName) {
        return find.where().eq("name", projectName).eq("owner", userName).findUnique();
    }
    
    public static Map<String, String> vcsTypes() {
        return new Options(
                "project.new.vcsType.git",
                "project.new.vcsType.subversion");
    }

    /**
     * 해당 프로젝트가 존재하는지 여부를 검사합니다. 해당 파라미터에 대하여 프로젝트가 존재하면 true, 존재하지 않으면 false를 반환합니다.
     * 
     * @param userName
     * @param projectName
     * @return
     */
    public static boolean isProject(String userName, String projectName) {
        int findRowCount = find.where().eq("name", projectName).eq("owner", userName).findRowCount();
        return (findRowCount != 0) ? true : false;
    }
    

    /**
     * 프로젝트 이름을 해당 이름(projectName)으로 변경이 가능한지 검사합니다.
     * 
     * @param id
     * @param userName
     * @param projectName
     * @return
     */
    public static boolean projectNameChangeable(Long id, String userName, String projectName) {
        int findRowCount = find.where().eq("name", projectName).eq("owner", userName).ne("id", id).findRowCount();
        return (findRowCount == 0) ? true : false;
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
            if(ProjectUser.checkOneMangerPerOneProject(project.id)) {
                projects.remove(project);
            }
        }
        
        return projects;
    }
    
    /**
     * 해당 유저가 속해있는 프로젝트들의 리스트를 제공합니다.
     * 
     * @param ownerId
     * @return
     */
    public static List<Project> findProjectsByMember(Long userId) {
        return find.where()
                .eq("projectUser.user.id", userId).findList();
    }
}
