package models;

import java.util.List;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

import com.avaje.ebean.Ebean;

import play.db.ebean.Model;

/**
 * @author "Hwi Ahn"
 * 
 */
@Entity
public class RolePermission extends Model {
    private static final long serialVersionUID = 1L;

    @Id
    public Long id;
    @ManyToOne
    public Role role;
    @ManyToOne
    public Permission permission;

    public RolePermission(Long roleId, Long permissionId) {
        this.role = Role.findById(id);
        this.permission = Permission.findById(permissionId);
    }

    private static Finder<Long, RolePermission> find = new Finder<Long, RolePermission>(
            Long.class, RolePermission.class);
    
    public static RolePermission findById(Long id) {
        return find.where().eq("id", id).findUnique();
    }
        
    /**
     * 해당 롤이 가지고 있는 퍼미션들의 리스트를 반환합니다.
     * 
     * @param roleId
     * @return
     */
    public static List<Permission> findPermissionsByRole(Long roleId) {
        return Ebean.find(Permission.class).where()
                .eq("rolePermissions.role.id", roleId).findList();
    }

    /**
     * 해당 롤-퍼미션의 아이디와 관계되어있는 롤을 찾아 반환합니다.
     * 
     * @param id
     * @return
     */
    public static Role findRoleById(Long id) {
        return Ebean.find(Role.class).where().eq("rolePermissions.id", id)
                .findUnique();
    }

    /**
     * 해당 롤-퍼미션과 관계되어있는 롤이 가지고 있는 퍼미션들의 리스트를 반환합니다.
     * 
     * @param id
     * @return
     */
    public static List<Permission> findPermissionsById(Long id) {
        return RolePermission.findPermissionsByRole(RolePermission
                .findRoleById(id).id);
    }
    
    /**
     * 해당 롤과 관계된 롤-퍼미션들의 리스트를 반환합니다.
     * 
     * @param roleId
     * @return
     */
    public static List<RolePermission> findByRole(Long roleId) {
        return find.where().eq("role.id", roleId).findList();
    }
}
