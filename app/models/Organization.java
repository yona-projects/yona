/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Keesun Baik, Wansoon Park, ChangSung Kim
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

import models.enumeration.ProjectScope;
import models.enumeration.RoleType;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import utils.ReservedWordsValidator;

import javax.persistence.*;
import java.util.*;

@Entity
public class Organization extends Model {

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

    public String descr;

    public void add(OrganizationUser ou) {
        this.users.add(ou);
    }

    public static Organization findByName(String name) {
        return find.where().eq("name", name).findUnique();
    }

    public static boolean isNameExist(String name) {
        int findRowCount = find.where().ieq("name", name).findRowCount();
        return (findRowCount != 0);
    }

    public List<Project> getVisiableProjects(User user) {
        List<Project> result = new ArrayList<>();
        if(OrganizationUser.isAdmin(this.id, user.id)) {
            // 모든 프로젝트
            result.addAll(this.projects);
        } else if(OrganizationUser.isMember(this.id, user.id)) {
            // private 프로젝트를 제외한 모든 프로젝트와 자신이 멤버로 속한 프로젝트
            for(Project p : this.projects) {
                if(p.projectScope != ProjectScope.PRIVATE || ProjectUser.isMember(user.id, p.id)) {
                    result.add(p);
                }
            }
        } else {
            // public 프로젝트와 자신이 멤버로 속한 프로젝트
            for(Project p : this.projects) {
                if(p.projectScope == ProjectScope.PUBLIC || ProjectUser.isMember(user.id, p.id)) {
                    result.add(p);
                }
            }
        }

        // 정렬
        Collections.sort(result, new Comparator<Project>() {
            @Override
            public int compare(Project p1, Project p2) {
                return p1.name.compareTo(p2.name);
            }
        });

        return result;
    }

}
