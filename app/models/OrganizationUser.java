package models;

import java.util.List;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import models.enumeration.RoleType;
import play.db.ebean.Model;

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
    public static List<OrganizationUser> findByAdmin(Long userId) {
        return find.where().eq("role", Role.findByRoleType(RoleType.ORG_ADMIN))
                    .eq("user.id", userId)
                    .findList();
    }
    public static boolean isAdmin(Long organizationId, Long userId) {
        int rowCount = find.where().eq("organization.id", organizationId)
                            .eq("user.id", userId)
                            .eq("role.id", Role.findByRoleType(RoleType.ORG_ADMIN).id)
                            .findRowCount();
        return rowCount > 0;
    }
}
