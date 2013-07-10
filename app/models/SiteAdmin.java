package models;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToOne;

import play.db.ebean.Model;

@Entity
public class SiteAdmin extends Model {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    @Id
    public Long id;

    @OneToOne
    public User admin;

    public static final Model.Finder<Long, SiteAdmin> find = new Finder<>(Long.class, SiteAdmin.class);

    public static boolean exists(User user) {
        return user != null && find.where().eq("admin.id", user.id).findRowCount() > 0;
    }
}
