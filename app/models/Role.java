package models;

import java.util.List;
import java.util.Set;

import play.db.ebean.Model;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;

/**
 * @author "Hwi Ahn"
 */
@Entity
public class Role extends Model {
    private static final long serialVersionUID = 1L;
    public static final Long MANAGER = 1l;
    public static final Long MEMBER = 2l;
    public static final Long SITEMANAGER = 3l;
    
    @Id
    public Long id;
    public String name;
    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL)
    public Set<RolePermission> rolePermissions;
    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL)
    public Set<ProjectUser> projectUsers;

    private static Finder<Long, Role> find = new Finder<Long, Role>(Long.class,
            Role.class);

    public static Role findById(Long id) {
        return find.where().eq("id", id).findUnique();
    }

    public static Role findByName(String name) {
        return find.where().eq("name", name).findUnique();
    }

    /**
     * 프로젝트와 관련된 롤들의 목록을 반환합니다.
     * 
     * @return
     */
    public static List<Role> getAllProjectRoles() {
        List<Role> projectRoles = find.where().ne("id", SITEMANAGER)
                .findList();
        return projectRoles;
    }

    /**
     * 해당 롤에 관련된 퍼미션들의 리스트를 반환합니다.
     * 
     * @param id
     * @return
     */
    public static List<Permission> findPermissionsById(Long id) {
        return RolePermission.findPermissionsByRole(id);
    }
}
