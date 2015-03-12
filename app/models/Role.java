/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author Hwi Ahn
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

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Role extends Model {
    private static final long serialVersionUID = 1L;
    public static final Finder<Long, Role> find = new Finder<>(Long.class,
            Role.class);

    @Id
    public Long id;

    public String name;
    public boolean active;

    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL)
    public List<ProjectUser> projectUsers;

    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL)
    public List<OrganizationUser> organizationUsers;

    public static Role findById(Long id) {
        return find.byId(id);
    }

    public static Role findByRoleType(RoleType roleType) {
        return find.byId(roleType.roleType());
    }

    public static Role findByName(String name) {
        return find.where().eq("name", name).findUnique();
    }

    public static Role findOrganizationRoleByIds(Long userId, Long organizationId) {
        return find.where()
                .eq("organizationUsers.user.id", userId)
                .eq("organizationUsers.organization.id", organizationId).findUnique();
    }

    public static Role findRoleByIds(Long userId, Long projectId) {
        return find.where()
                .eq("projectUsers.user.id", userId)
                .eq("projectUsers.project.id", projectId).findUnique();
    }

    public static List<Role> findProjectRoles() {
        List<Long> projectRoleIds = new ArrayList<>();
        projectRoleIds.add(RoleType.MANAGER.roleType());
        projectRoleIds.add(RoleType.MEMBER.roleType());

        return find.where()
                .in("id", projectRoleIds)
                .findList();
    }

    public static List<Role> findOrganizationRoles() {
        List<Long> organizationRoleIds = new ArrayList<>();
        organizationRoleIds.add(RoleType.ORG_ADMIN.roleType());
        organizationRoleIds.add(RoleType.ORG_MEMBER.roleType());

        return find.where()
                .in("id", organizationRoleIds)
                .findList();
    }
}
