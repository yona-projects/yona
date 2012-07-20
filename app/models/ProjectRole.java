package models;

import javax.persistence.Entity;
import javax.persistence.Id;

import play.db.ebean.Model;

/**
 * @author "Hwi Ahn"
 *
 */
@Entity
public class ProjectRole extends Model {
    private static final long serialVersionUID = 1L;

    @Id
    public Long id;
    public String name;
    
    private static Finder<Long, ProjectRole> find = new Finder<Long, ProjectRole>(
            Long.class, ProjectRole.class);
    
    public static ProjectRole findById(Long id) {
        return find.where().eq("id", id).findUnique();
    }
    
    public static ProjectRole findByName(String name) {
        return find.where().eq("name", name).findUnique();
    }
    
}
