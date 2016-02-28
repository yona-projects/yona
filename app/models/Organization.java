/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Keesun Baik, Wansoon Park, ChangSung Kim
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

import models.enumeration.RequestState;
import models.enumeration.ResourceType;
import models.resource.GlobalResource;
import models.resource.Resource;
import models.resource.ResourceConvertible;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import play.db.ebean.Transactional;
import playRepository.PlayRepository;
import playRepository.RepositoryService;
import utils.ReservedWordsValidator;

import javax.persistence.*;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.*;

@Entity
public class Organization extends Model implements ResourceConvertible {

    private static final long serialVersionUID = -1L;

    public static final Finder<Long, Organization> find = new Finder<>(Long.class, Organization.class);

    @Id
    public Long id;

    @Constraints.Pattern(value = "^" + User.LOGIN_ID_PATTERN + "$", message = "user.wrongloginId.alert")
    @Constraints.Required
    @Constraints.ValidateWith(ReservedWordsValidator.class)
    public String name;

    @Formats.DateTime(pattern = "yyyy-MM-dd")
    public Date created;

    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL)
    public List<Project> projects;

    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL)
    public List<OrganizationUser> users;

    @ManyToMany(mappedBy = "enrolledOrganizations")
    public List<User> enrolledUsers;

    public String descr;

    public void add(OrganizationUser ou) {
        this.users.add(ou);
    }

    public static Organization findByName(String name) {
        return find.where().ieq("name", name).findUnique();
    }

    public static boolean isNameExist(String name) {
        int findRowCount = find.where().ieq("name", name).findRowCount();
        return (findRowCount != 0);
    }

    public boolean isLastAdmin(User currentUser) {
        return OrganizationUser.isAdmin(this, currentUser) && getAdmins().size() == 1;
    }

    @Transactional
    public void cleanEnrolledUsers() {
        List<User> enrolledUsers = this.enrolledUsers;
        List<User> acceptedUsers = new ArrayList<>();
        List<OrganizationUser> members = this.users;
        for(OrganizationUser organizationUser : members) {
            User user = organizationUser.user;
            if(enrolledUsers.contains(user)) {
                acceptedUsers.add(user);
            }
        }
        for(User user : acceptedUsers) {
            user.cancelEnroll(this);
            NotificationEvent.afterOrganizationMemberRequest(this, user, RequestState.ACCEPT);
        }
    }

    public List<Project> getVisibleProjects(User user) {
        List<Project> result = new ArrayList<>();
        if(OrganizationUser.isAdmin(this.id, user.id)) {
            result.addAll(this.projects);
        } else if(OrganizationUser.isMember(this.id, user.id)) {
            for(Project project : this.projects) {
                if(!project.isPrivate() || user.isMemberOf(project)) {
                    result.add(project);
                }
            }
        } else {
            for(Project project : this.projects) {
                if(project.isPublic() || user.isMemberOf(project)) {
                    result.add(project);
                }
            }
        }

        Collections.sort(result, new Comparator<Project>() {
            @Override
            public int compare(Project p1, Project p2) {
                return p1.name.compareTo(p2.name);
            }
        });

        return result;
    }

    /**
     * Find organizations which contains {@code userLoginId} as member.
     *
     * @param userLoginId
     * @return
     */
    public static List<Organization> findOrganizationsByUserLoginId(String userLoginId) {
        return find.where().eq("users.user.loginId", userLoginId)
                .orderBy("created DESC")
                .findList();
    }

    /**
     * As resource.
     *
     * @return the resource
     */
    @Override
    public Resource asResource() {
        return new GlobalResource() {

            @Override
            public String getId() {
                return id.toString();
            }

            @Override
            public ResourceType getType() {
                return ResourceType.ORGANIZATION;
            }

        };
    }

    public List<OrganizationUser> getAdmins() {
        return OrganizationUser.findAdminsOf(this);
    }

    public void updateWith(Organization modifiedOrganization) throws IOException, ServletException {
        if(!this.name.equals(modifiedOrganization.name)) {
            updateProjects(modifiedOrganization.name);
        }
        modifiedOrganization.update();
    }

    private void updateProjects(String newOwner) throws IOException, ServletException {
        for(Project project : projects) {
            if(!newOwner.equals(project.owner)) {
                PlayRepository repository = RepositoryService.getRepository(project);
                repository.move(project.owner, project.name, newOwner, project.name);
            }
            project.owner = newOwner;
            project.update();
        }
    }

}

