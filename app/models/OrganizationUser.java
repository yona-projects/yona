/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @author Keesun
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package models;

import models.enumeration.RoleType;
import play.db.ebean.Model;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import java.util.List;

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

    public static boolean isAdmin(Organization organization, User user) {
        return contains(organization, user, RoleType.ORG_ADMIN);
    }

    public static boolean isAdmin(Long organizationId, Long userId) {
        return contains(organizationId, userId, RoleType.ORG_ADMIN);
    }

    public static boolean isGuest(Organization organization, User user) {
        return roleTypeOf(user, organization).equals(RoleType.GUEST.name());
    }

    public static boolean isMember(Organization organization, User user) {
        return contains(organization, user, RoleType.ORG_MEMBER);
    }

    public static boolean isMember(Long organizationId, Long userId) {
        return contains(organizationId, userId, RoleType.ORG_MEMBER);
    }

    public static String roleTypeOf(User user, Organization organization) {
        if(user == null) {
            return RoleType.ANONYMOUS.name();
        } else if(user.isSiteManager()) {
            return RoleType.SITEMANAGER.name();
        } else if(user.isAnonymous()) {
            return RoleType.ANONYMOUS.name();
        } else {
            Role role = Role.findOrganizationRoleByIds(user.id, organization.id);
            if (role == null) {
                return RoleType.GUEST.name();
            } else {
                return role.name;
            }
        }
    }

    private static boolean contains(Organization organization, User user, RoleType roleType) {
        if (organization == null) {
            return false;
        }
        if (user == null || user.isAnonymous()) {
            return false;
        }
        return contains(organization.id, user.id, roleType);
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

    public static boolean exist(Long organizationId, Long userId) {
        return findByOrganizationIdAndUserId(organizationId, userId) != null;
    }

    public static List<OrganizationUser> findByUser(User user, int size) {
        return find.where().eq("user", user)
                .order().asc("organization.name")
                .setMaxRows(size)
                .findList();
    }
}
