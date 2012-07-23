package models;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

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
    public String name;
    
    private static Finder<Long, Permission> find = new Finder<Long, Permission>(
            Long.class, Permission.class);
    
    public static Permission findByName(String name) {
        return find.where().eq("name", name).findUnique();
    }
}
