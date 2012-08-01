package models;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import com.avaje.ebean.Ebean;
import play.db.ebean.Model;
import utils.Constants;

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

    public static void create(Long userId, Long projectId, Long roleId) {
        ProjectUser projectUser = new ProjectUser(userId, projectId, roleId);
        projectUser.save();
    }

    /**
     * 해당 프로젝트에 가입된 해당 유저를 프로젝트에서 탈퇴시킵니다.
     * 
     * @param userId
     * @param projectId
     */
    public static void delete(Long userId, Long projectId) {
        ProjectUser.findByIds(userId, projectId).delete();
    }

    /**
     * 유저에게 새로운 롤을 부여합니다.
     * 
     * @param userId
     * @param projectId
     * @param roleId
     */
    public static void assignRole(Long userId, Long projectId, Long roleId) {
        if (find.where().eq("user.id", userId).eq("project.id", projectId)
                .findRowCount() == 0) {
            ProjectUser.create(userId, projectId, roleId);
        } else {
            new ProjectUser(userId, projectId, roleId).update(ProjectUser
                    .findByIds(userId, projectId).id);
        }
    }

    /**
     * 해당 유저, 프로젝트 값을 갖는 ProjectUser 오브젝트를 반환합니다.
     * 
     * @param userId
     * @param projectId
     * @return
     */
    public static ProjectUser findByIds(Long userId, Long projectId) {
        return find.where().eq("user.id", userId).eq("project.id", projectId)
                .findUnique();
    }

    /**
     * 해당 프로젝트에 속하는 유저들의 리스트를 제공합니다.
     * 
     * @param projectId
     * @return
     */
    public static List<User> findUsersByProject(Long projectId) {
        return Ebean.find(User.class)
                .where()
                    .eq("projectUser.project.id", projectId)
                .findList();
    }

    /**
     * 해당 유저가 속해있는 프로젝트들의 리스트를 제공합니다.
     * 
     * @param ownerId
     * @return
     */
    public static List<Project> findProjectsByOwner(Long ownerId) {
        return Ebean.find(Project.class)
                .where()
                    .eq("projectUser.user.id", ownerId)
                .findList();
    }

    /**
     * 해당 유저가 해당 프로젝트에서 가지고 있는 롤을 제공합니다.
     * 
     * @param userId
     * @param projectId
     * @return
     */
    public static Role findRoleByIds(Long userId, Long projectId) {
        return Ebean.find(Role.class)
                .where()
                    .eq("projectUsers.user.id", userId)
                    .eq("projectUsers.project.id", projectId)
                .findUnique();
    }

    /**
     * 해당 유저가 해당 프로젝트에서 가지고 있는 퍼미션들의 리스트를 제공합니다.
     * 
     * @param userId
     * @param projectId
     * @return
     */
    public static List<Permission> findPermissionsByIds(Long userId,
            Long projectId) {
        return Role.findPermissionsById(ProjectUser.findRoleByIds(userId, projectId).id);
    }

    /**
     * 해당 프로젝트에 최소 1명 이상의 관리자가 남아있는지 확인합니다.
     * 
     * @param projectId
     * @return
     */
    public static boolean isManager(Long projectId) {
        int findRowCount = find
                .where()
                    .eq("role.id", Role.MANAGER)
                    .eq("project.id", projectId)
                .findRowCount();
        return (findRowCount > 1) ? true : false;
    }

    /**
     * 해당 프로젝트에 참가하고 있는 유저의 목록을 제공합니다.
     * 
     * @return
     */
    public static Map<String, String> options(Long projectId) {
        LinkedHashMap<String, String> options = new LinkedHashMap<String, String>();
        for (User user : findUsersByProject(projectId)) {
            options.put(user.id.toString(), user.loginId);
        }
        return options;
    }
    
    
    public static boolean permissionCheck(Long userId, Long projectId, String resource, String operation) {
        int findRowCount = Ebean.find(Permission.class)
                                .where()
                                    .eq("rolePermissions.role.projectUsers.user.id", userId)
                                    .eq("rolePermissions.role.projectUsers.project.id", projectId)
                                    .eq("resource", resource)
                                    .eq("operation", operation)
                                .findRowCount();
        return (findRowCount != 0) ? true : false;
    }
}
