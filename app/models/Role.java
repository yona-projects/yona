package models;

import java.util.ArrayList;
import java.util.List;
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
    @ManyToMany
    public List<Permission> permissions = new ArrayList<Permission>();

    private static Finder<Long, Role> find = new Finder<Long, Role>(
        Long.class, Role.class);

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public static Role findById(Long id) {
        return find.where().eq("id", id).findUnique();
    }

    public static Role findByName(String name) {
        return find.where().eq("name", name).findUnique();
    }
    
    public List<Permission> getPermissions() {
        return permissions;
    }

}
