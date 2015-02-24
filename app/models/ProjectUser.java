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

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity
public class ProjectUser extends Model {

    private static final long serialVersionUID = 1L;

    private static Finder<Long, ProjectUser> find = new Finder<>(Long.class, ProjectUser.class);

    @Id
    public Long id;

    @ManyToOne
    public User user;

    @ManyToOne
    public Project project;

    @ManyToOne
    public Role role;

    public ProjectUser(Long userId, Long projectId, Long roleId) {
        this.user = User.find.byId(userId);
        this.project = Project.find.byId(projectId);
        this.role = Role.findById(roleId);
    }

    public static void create(Long userId, Long projectId, Long roleId) {
        ProjectUser projectUser = new ProjectUser(userId, projectId, roleId);
        projectUser.save();
    }

    public static void delete(Long userId, Long projectId) {
        ProjectUser.findByIds(userId, projectId).delete();
    }

    public static void assignRole(Long userId, Long projectId, Long roleId) {
        ProjectUser projectUser = ProjectUser.findByIds(userId, projectId);

        if (projectUser == null) {
            ProjectUser.create(userId, projectId, roleId);
        } else {
            new ProjectUser(userId, projectId, roleId).update(projectUser.id);
        }
    }

    /**
     * @param userId the user id
     * @param projectId the project id
     * @param roleType the role type
     *
     * @see {@link ProjectUser#assignRole}
     * @see {@link RoleType#roleType()}
     */
    public static void assignRole(Long userId, Long projectId, RoleType roleType) {
        assignRole(userId, projectId, roleType.roleType());
    }

    public static ProjectUser findByIds(Long userId, Long projectId) {
        return find.where().eq("user.id", userId).eq("project.id", projectId)
                .ne("role.id", RoleType.SITEMANAGER.roleType()).findUnique();
    }

    public static List<ProjectUser> findMemberListByProject(Long projectId) {
        return find.fetch("user").fetch("role", "name").where()
                .eq("project.id", projectId).ne("role.id", RoleType.SITEMANAGER.roleType())
                .orderBy("user.name ASC")
                .findList();
    }

    public static boolean checkOneMangerPerOneProject(Long userId, Long projectId) {
        int findRowCount = find.where().eq("role.id", RoleType.MANAGER.roleType())
                .eq("project.id", projectId).ne("user.id", userId).findRowCount();

        return (findRowCount <= 0);
    }

    public static boolean isManager(Long userId, Long projectId) {
        int findRowCount = find.where().eq("user.id", userId)
                .eq("role.id", RoleType.MANAGER.roleType()).eq("project.id", projectId)
                .findRowCount();
        return (findRowCount != 0);
    }

    public static boolean isMember(Long userId, Long projectId) {
        if (userId == null) {
            return false;
        }
        int findRowCount = find.where().eq("user.id", userId).eq("project.id", projectId)
                .findRowCount();
        return (findRowCount != 0);
    }

    /**
     * @param projectId the project id
     * @return
     *
     * @see {@link User#findUsersByProject(Long)}
     */
    public static Map<String, String> options(Long projectId) {
        LinkedHashMap<String, String> options = new LinkedHashMap<>();
        for (User user : User.findUsersByProject(projectId)) {
            options.put(user.id.toString(), user.loginId);
        }
        return options;
    }

    public static ProjectUser findById(Long id) {
        return find.byId(id);
    }

    public static List<ProjectUser> findAll(){
        return find.all();
    }

    public static boolean isAllowedToNotice(User user, Project project) {
        if(user.isAnonymous()) {
            return false;
        }
        if(user.isSiteManager()) {
            return true;
        }
        return ProjectUser.isMember(user.id, project.id) || ProjectUser.isManager(user.id, project.id);
    }

    public static String roleOf(String loginId, Project project) {
        User user = User.findByLoginId(loginId);
       return roleOf(user, project);
    }

    public static String roleOf(User user, Project project) {
        if(user == null) {
            return RoleType.ANONYMOUS.getLowerCasedName();
        }

        if(user.isSiteManager()) {
            return RoleType.SITEMANAGER.getLowerCasedName();
        }

        if(user.isAnonymous()) {
           return RoleType.ANONYMOUS.getLowerCasedName();
        } else {
            Role role = Role.findRoleByIds(user.id, project.id);
            if(role == null) {
                return RoleType.GUEST.getLowerCasedName();
            } else {
                // manager or member
                return role.name.toLowerCase();
            }
        }
    }

    public static boolean isAllowedToSettings(String loginId, Project project) {
        if(loginId == null) {
            return false;
        }

        User user = User.findByLoginId(loginId);
        if(user.isAnonymous()) {
            return false;
        }
        return user.isSiteManager() || ProjectUser.isManager(user.id, project.id);
    }

    public static boolean isGuest(Project project, User user) {
        return roleOf(user, project).equals(RoleType.GUEST.getLowerCasedName());
    }
}
