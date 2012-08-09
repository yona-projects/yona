package models;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import play.db.ebean.Model;

/**
 * @author "Hwi Ahn"
 * 
 */
@Entity
public class RolePermission extends Model {
    private static final long serialVersionUID = 1L;
    private static Finder<Long, RolePermission> find = new Finder<Long, RolePermission>(
            Long.class, RolePermission.class);
    
    @Id
    public Long id;
    
    @ManyToOne
    public Role role;
    
    @ManyToOne
    public Permission permission;
}
