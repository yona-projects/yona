package models;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import models.enumeration.RoleType;
import play.db.ebean.Model;

/**
 * The Class ProjectUser.
 *
 * 각 프로젝트별 멤버와 멤버의 역할정보를 처리한다.
 *
 */
@Entity
public class ProjectUser extends Model {

    private static final long serialVersionUID = 1L;

    private static Finder<Long, ProjectUser> find = new Finder<>(Long.class, ProjectUser.class);

    @Id
    public Long id;

    @ManyToOne
    public User user;

    @ManyToOne
    public Project project;

    @ManyToOne
    public Role role;

    /**
     * 프로젝트 멤버정보를 반환한다.
     *
     * {@code userId} 로 사용자 정보를 가져온다.
     * {@code projectId} 로 프로젝트 정보를 가져온다.
     * {@code roleId} 로 역할 정보를 가져온다.
     *
     * @param userId 사용자 아이디
     * @param projectId 프로젝트 아이디
     * @param roleId 역할 아이디
     */
    public ProjectUser(Long userId, Long projectId, Long roleId) {
        this.user = User.find.byId(userId);
        this.project = Project.find.byId(projectId);
        this.role = Role.findById(roleId);
    }

    /**
     * 새로운 프로젝트 멤버를 추가한다.
     *
     * {@code userId}, {@code projectId}, {@code roleId} 로 프로젝트의 멤버를 추가한다.
     *
     * @param userId 사용자 아이디
     * @param projectId 프로젝트 아이디
     * @param roleId 역할 아이디
     */
    public static void create(Long userId, Long projectId, Long roleId) {
        ProjectUser projectUser = new ProjectUser(userId, projectId, roleId);
        projectUser.save();
    }

    /**
     * {@code projectId} 프로젝트에 소속된 멤버 {@code userId}를 삭제한다.
     *
     * @param userId 사용자 아이디
     * @param projectId 프로젝트 아이디
     */
    public static void delete(Long userId, Long projectId) {
        ProjectUser.findByIds(userId, projectId).delete();
    }

    /**
     * 신규로 프로젝트 멤버를 추가하거나 기존 멤버에 새로운 역할정보를 할당한다.
     *
     * {@code projectId} 와 {@code userId} 로 프로젝트 멤버 정보 {@code projectUser} 를 가져오고
     * {@code projectUser} 가 null 이면 신규로 프로젝트 멤버 정보를 추가하고
     * null 이 아니면 새로운 {@code roleId}를 할당한다.
     *
     * @param userId 사용자 아이디
     * @param projectId 프로젝트 아이디
     * @param roleId 역할 아이디
     */
    public static void assignRole(Long userId, Long projectId, Long roleId) {
        ProjectUser projectUser = ProjectUser.findByIds(userId, projectId);

        if (projectUser == null) {
            ProjectUser.create(userId, projectId, roleId);
        } else {
            new ProjectUser(userId, projectId, roleId).update(projectUser.id);
        }
    }

    /**
     * 신규로 프로젝트 멤버를 추가하거나 기존 멤버에 새로운 역할정보를 할당한다.
     *
     * @param userId the user id
     * @param projectId the project id
     * @param roleType the role type
     *
     * @see {@link ProjectUser#assignRole}
     * @see {@link RoleType#roleType()}
     */
    public static void assignRole(Long userId, Long projectId, RoleType roleType) {
        assignRole(userId, projectId, roleType.roleType());
    }

    /**
     * {@code userId} 와 {@code projectId} 로 사이트 관리자를 제외한 프로젝트 멤버 정보({@link ProjectUser})를 반환한다.
     *
     * @param userId 사용자 아이디
     * @param projectId 프로젝트 아이이
     * @return 프로젝트 멤버 정보({@link ProjectUser})
     */
    public static ProjectUser findByIds(Long userId, Long projectId) {
        return find.where().eq("user.id", userId).eq("project.id", projectId)
                .ne("role.id", RoleType.SITEMANAGER.roleType()).findUnique();
    }

    /**
     * {@code projectId}로 사이트 관리자를 제외한 해당 프로젝트 멤버({@link ProjectUser}) 목록을 가져온다.
     *
     * @param projectId 프로젝트 아이디
     * @return 프로젝트 멤버 정보({@link ProjectUser}) 목록
     */
    public static List<ProjectUser> findMemberListByProject(Long projectId) {
        return find.fetch("user", "loginId").fetch("role", "name").where()
                .eq("project.id", projectId).ne("role.id", RoleType.SITEMANAGER.roleType())
                .findList();
    }

    /**
     * 해당 프로젝트에 {@code userId} 를 제외한 최소 1명 이상의 관리자가 남아있는지 확인한다.
     *
     * @param userId 사용자 아이디
     * @param projectId 프로젝트 아이디
     * @return {@code userId}가 프로젝트의 유일한 관리자이면 true, 아니면 false
     */
    public static boolean checkOneMangerPerOneProject(Long userId, Long projectId) {
        int findRowCount = find.where().eq("role.id", RoleType.MANAGER.roleType())
                .eq("project.id", projectId).ne("user.id", userId).findRowCount();

        return (findRowCount > 0) ? false : true;
    }

    /**
     * 해당 사용자가 프로젝트의 관리자인지 확인한다.
     *
     * {@code userId} 와 {@code projectId}, {@link RoleType#MANAGER}로 카운트 정보를 가져온다.
     *
     * @param userId 사용자 아이디
     * @param projectId 프로젝트 아이디
     * @return 카운트가 0이 아니면 true, 0이면 false
     */
    public static boolean isManager(Long userId, Long projectId) {
        int findRowCount = find.where().eq("user.id", userId)
                .eq("role.id", RoleType.MANAGER.roleType()).eq("project.id", projectId)
                .findRowCount();
        return (findRowCount != 0) ? true : false;
    }

    /**
     * 해당 사용자가 프로젝트 멤버인지 확인한다.
     *
     * {@code userId} 와 {@code projectId} 로 카운트 정보를 가져온다.
     *
     * @param userId 사용자 아이디
     * @param projectId 프로젝트 아이디
     * @return 카운트가 0이 아니면 true, 0이면 false
     */
    public static boolean isMember(Long userId, Long projectId) {
        if (userId == null) {
            return false;
        }
        int findRowCount = find.where().eq("user.id", userId).eq("project.id", projectId)
                .findRowCount();
        return (findRowCount != 0) ? true : false;
    }

    /**
     * 해당 프로젝트의 멤버 목록을 반환한다.
     *
     * {@code projectId} 로 사이트 관리자를 제외한 프로젝트 멤버 목록을 가져온다.
     * key({@code userId}), value({@code loginId}) 형태의 {@link Map}을 반환한다.
     *
     * @param projectId the project id
     * @return key({@code userId}), value({@code loginId}) 형태의 {@link Map}
     *
     * @see {@link User#findUsersByProject(Long)}
     */
    public static Map<String, String> options(Long projectId) {
        LinkedHashMap<String, String> options = new LinkedHashMap<>();
        for (User user : User.findUsersByProject(projectId)) {
            options.put(user.id.toString(), user.loginId);
        }
        return options;
    }

    /**
     * {@code id}로 프로젝트 멤버 정보({@link ProjectUser})를 가져온다.
     *
     * @param id 아이디
     * @return 프로젝트 멤버 정보({@link ProjectUser})
     */
    public static ProjectUser findById(Long id) {
        return find.byId(id);
    }

    /**
     * 모든 프로젝트 멤버 정보({@link ProjectUser}) 목록을 가져온다.
     *
     * @return 프로젝트 멤버 정보({@link ProjectUser}) 목록
     */
    public static List<ProjectUser> findAll(){
        return find.all();
    }

    /**
     * 사용자가 해당 프로젝트의 게시글 공지권한이 있는지 확인한다.
     *
     * {@code user}가 anonymous이면 false를 반환한다.
     * {@code user}가 사이트 관리자이면 true를 반환한다.
     * {@code user.id} 와 {@code project.id} 로 조회하여 해당 프로젝트의 멤버 또는 관리자 일경우 true를 반환한다.
     *
     * @param user 사용자
     * @param project 프로젝트
     * @return 프로젝트 멤버 또는 관리자이면 true, Anonymous이면 false
     */
    public static boolean isAllowedToNotice(User user, Project project) {
        if(user.isAnonymous()) {
            return false;
        }
        if(user.isSiteManager()) {
            return true;
        }
        if(ProjectUser.isMember(user.id, project.id) || ProjectUser.isManager(user.id, project.id)) {
            return true;
        }
        return false;
    }

    /**
     * 사용자의 해당 프로젝트내 역할을 소문자로 반환한다.
     *
     * {@code loginId} 가 null이면 {@link RoleType#ANONYMOUS}를 소문자로 반환한다.
     * {@code loginId} 로 조회한 사용자가 null 이면 {@link RoleType#ANONYMOUS}를 소문자로 반환한다.
     * {@code loginId} 로 조회한 사용자가 사이트 관리자이면 {@link RoleType#SITEMANAGER}를 소문자로 반환한다.
     * {@code loginId} 로 조회한 사용자가 Anomymous가 아니면 해당 사용자의 역할을 가져오고
     * 역할이 null이 아니면 해당 역할명을 소문자로 반환한다.
     * 역할이 null이면 {@link RoleType#GUEST}를 소문자로 반환한다.
     * {@code loginId} 로 조회한 사용자가 Anomymous이면 {@link RoleType#ANONYMOUS}를 소문자로 반환한다.
     *
     * @param loginId 로그인 아이디
     * @param project 프로젝트
     * @return 소문자로 변환된 역할명
     */
    public static String roleOf(String loginId, Project project) {
        RoleType roleType = RoleType.ANONYMOUS;
        if(loginId == null) {
            return roleType.getLowerCasedName();
        }

        User user = User.findByLoginId(loginId);
        if(user == null) {
            return roleType.getLowerCasedName();
        }

        if(user.isSiteManager()) {
            return RoleType.SITEMANAGER.getLowerCasedName();
        } else if(!user.isAnonymous()) {
            Role role = Role.findRoleByIds(user.id, project.id);
            // manager or member
            if(role != null) {
                return role.name.toLowerCase();
            } else {
                return RoleType.GUEST.getLowerCasedName();
            }
        }
        return roleType.getLowerCasedName();
    }

    /**
     * 해당 사용자가 설정 권한이 있는지 확인한다.
     *
     * 프로젝트 페이지의 우측 사이드 메뉴에 사용된다.
     *
     * {@code loginId} 가 null 이면 false를 반환한다.
     * {@code loginId} 가 null 이 아니면 사용자 정보를 가져오고
     * 사용자가 anonymous이면 false를 반환한다.
     * 사용자가 사이트 관리자 또는 프로젝트 관리자 일 경우 true를 반환한다.
     * 사용자가 사이트 관리자나 프로젝트 관리자가 아닐경우(멤버) false를 반환한다.
     *
     * @param loginId 로그인 아이디
     * @param project 프로젝트
     * @return 사이트 관리자 또는 프로젝트 관리자는 true, 아니면 false
     */
    public static boolean isAllowedToSettings(String loginId, Project project) {
        if(loginId == null) {
            return false;
        }

        User user = User.findByLoginId(loginId);
        if(user.isAnonymous()) {
            return false;
        }
        if(user.isSiteManager() || ProjectUser.isManager(user.id, project.id)) {
            return true;
        }
        return false;
    }
}
