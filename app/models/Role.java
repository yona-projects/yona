package models;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import play.db.ebean.Model;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;

/**
 * @author "Hwi Ahn"
 */
@Entity
public class Role extends Model {
    private static final long serialVersionUID = 1L;

    @Id
    public Long id;
    public String name;
    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL)
    public Set<ProjectUser> projectUser;
    @ManyToMany
    public List<Permission> permissions = new ArrayList<Permission>();

    private static Finder<Long, Role> find = new Finder<Long, Role>(
        Long.class, Role.class);

    public static Role findById(Long id) {
        return find.byId(id);
    }

    public static Role findByName(String name) {
        return find.where().eq("name", name).findUnique();
    }
    
    /**
     * 프로젝트와 관련된 롤들의 목록을 제공합니다.
     * @return
     */
    public static List<Role> getAllProjectRoles() {
        List<Role> projectRoles = find.where().ne("name", "siteManager").findList();
        return projectRoles;
    }
    
    public List<Permission> getPermissions() {
        return permissions;
    }

}
