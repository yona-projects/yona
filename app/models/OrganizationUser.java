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
        return contains(organizationId, userId, RoleType.ORG_ADMIN);
    }

    public static boolean isMember(Long organizationId, Long userId) {
        return contains(organizationId, userId, RoleType.ORG_MEMBER);
    }

    private static boolean contains(Long organizationId, Long userId, RoleType roleType) {
        int rowCount = find.where().eq("organization.id", organizationId)
                .eq("user.id", userId)
                .eq("role.id", Role.findByRoleType(roleType).id)
                .findRowCount();
        return rowCount > 0;
    }

    public static void assignRole(Long userId, Long organizationId, Long roleId) {
        OrganizationUser organizationUser = OrganizationUser.findByOrganizationIdAndUserId(organizationId, userId);

        if (organizationUser == null) {
            OrganizationUser.create(userId, organizationId, roleId);
        } else {
            Role role = Role.findById(roleId);

            if (role != null) {
                organizationUser.role = role;
                organizationUser.update();
            }
        }
    }

    public static OrganizationUser findByOrganizationIdAndUserId(Long organizationId, Long userId) {
        return find.where().eq("user.id", userId)
                .eq("organization.id", organizationId)
                .findUnique();
    }

    public static void create(Long userId, Long organizationId, Long roleId) {
        OrganizationUser organizationUser = new OrganizationUser();
        organizationUser.user = User.find.byId(userId);
        organizationUser.organization = Organization.find.byId(organizationId);
        organizationUser.role = Role.findById(roleId);
        organizationUser.save();
    }

    public static void delete(Long organizationId, Long userId) {
        OrganizationUser organizationUser = OrganizationUser.findByOrganizationIdAndUserId(organizationId, userId);

        if (organizationUser != null) {
            organizationUser.delete();
        }
    }
}
