package models;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;

import models.enumeration.RoleType;
import models.task.Card;
import play.db.ebean.Model;

/**
 * @author "Hwi Ahn"
 *
 */
@Entity
public class ProjectUser extends Model {
    private static final long serialVersionUID = 1L;
    private static Finder<Long, ProjectUser> find = new Finder<Long, ProjectUser>(Long.class,
            ProjectUser.class);

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
        this.project = Project.find.byId(projectId);
        this.role = Role.findById(roleId);
    }

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
        ProjectUser projectUser = ProjectUser.findByIds(userId, projectId);

        if (projectUser == null) {
            ProjectUser.create(userId, projectId, roleId);
        } else {
            new ProjectUser(userId, projectId, roleId).update(projectUser.id);
        }
    }

    public static void assignRole(Long userId, Long projectId, RoleType roleType) {
        assignRole(userId, projectId, roleType.roleType());
    }

    /**
     * 해당 유저, 프로젝트 값을 갖는 ProjectUser 오브젝트를 제공합니다.
     * (Site manager는 hidden role로서 반환되지 않습니다.)
     *
     * @param userId
     * @param projectId
     * @return
     */
    public static ProjectUser findByIds(Long userId, Long projectId) {
        return find.where().eq("user.id", userId).eq("project.id", projectId)
                .ne("role.id", RoleType.SITEMANAGER.roleType()).findUnique();
    }

    /**
     * 해당 프로젝트에 가입한 맴버들의 Login ID와 그 맴버들의 Role의 이름을 제공합니다.
     * (Site manager는 hidden role로서 반환되지 않습니다.)
     *
     * @param projectId
     * @return
     */
    public static List<ProjectUser> findMemberListByProject(Long projectId) {
        return find.fetch("user", "loginId").fetch("role", "name").where()
                .eq("project.id", projectId).ne("role.id", RoleType.SITEMANAGER.roleType())
                .findList();
    }

    /**
     * 해당 프로젝트에 최소 1명 이상의 관리자가 남아있는지 확인합니다.
     *
     * @param projectId
     * @return
     */
    public static boolean checkOneMangerPerOneProject(Long projectId) {
        int findRowCount = find.where().eq("role.id", RoleType.MANAGER.roleType())
                .eq("project.id", projectId).findRowCount();
        return (findRowCount > 0) ? true : false;
    }

    /**
     * 해당 유저가 해당 프로젝트의 매니저 역할인지 확인합니다.
     *
     * @param userId
     * @param projectId
     * @return
     */
    public static boolean isManager(Long userId, Long projectId) {
        int findRowCount = find.where().eq("user.id", userId)
                .eq("role.id", RoleType.MANAGER.roleType()).eq("project.id", projectId)
                .findRowCount();
        return (findRowCount != 0) ? true : false;
    }

    /**
     * 해당 유저가 해당 프로젝트에 가입되어 있는지 확인합니다.
     *
     * @param userId
     * @param projectId
     * @return
     */
    public static boolean isMember(Long userId, Long projectId) {
        if (userId == null)
            return false;
        int findRowCount = find.where().eq("user.id", userId).eq("project.id", projectId)
                .findRowCount();
        return (findRowCount != 0) ? true : false;
    }

    /**
     * 해당 프로젝트에 참가하고 있는 유저의 목록을 제공합니다.
     *
     * @return
     */
    public static Map<String, String> options(Long projectId) {
        LinkedHashMap<String, String> options = new LinkedHashMap<String, String>();
        for (User user : User.findUsersByProject(projectId)) {
            options.put(user.id.toString(), user.loginId);
        }
        return options;
    }

	public static ProjectUser findById(Long id) {
		return find.byId(id);
	}
	public static List<ProjectUser> findAll(){
		return find.all();
	}
}
