package models;

import java.util.List;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

import play.db.ebean.Model;
import play.db.ebean.Model.Finder;

/**
 * @author "Hwi Ahn"
 *
 */
@Entity
public class Permission extends Model{
    private static final long serialVersionUID = 1L;
    
    @Id
    public Long id;
    public String resource;
    public String operation;
    @OneToMany(mappedBy = "permission", cascade = CascadeType.ALL)
    public Set<RolePermission> rolePermissions;
    
    private static Finder<Long, Permission> find = new Finder<Long, Permission>(
            Long.class, Permission.class);
    
    public static Permission findById(Long id) {
        return find.where().eq("id", id).findUnique();
    }
    
    /**
     * 해당 리소스와 오퍼레이션을 갖는 퍼미션을 반환합니다.
     * 
     * @param resource
     * @param operation
     * @return
     */
    public static Long findIdByResOp(String resource, String operation) {
        return find.where().eq("resource", resource).eq("operation", operation).findUnique().id;
    }
    
    /**
     * 해당 리소스를 갖는 퍼미션들의 리스트를 반환합니다.
     * 
     * @param resource
     * @return
     */
    public static List<Permission> findByResource(String resource) {
        return find.where().eq("resource", resource).findList();
    }
}
