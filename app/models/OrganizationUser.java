package models;

import play.db.ebean.Model;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import java.util.List;

/**
 * @author Keeun Baik
 */
@Entity
public class OrganizationUser extends Model {

    private static final long serialVersionUID = -1L;

    public static final Finder<Long, OrganizationUser> find = new Finder<>(Long.class, OrganizationUser.class);

    @Id
    public Long id;

    @ManyToOne
    public User user;

    @ManyToOne
    public Organization organization;

    @ManyToOne
    public Role role;

    public static List<OrganizationUser> findAdminsOf(Organization organization) {
        return find.where()
                .eq("organization", organization)
                .eq("role", Role.findByName("org_admin"))
                .findList();
    }
}
