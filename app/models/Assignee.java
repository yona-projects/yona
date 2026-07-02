/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author Yi EungJun
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

import org.apache.commons.collections.CollectionUtils;
import play.data.validation.Constraints.Required;
import io.ebean.Finder;
import io.ebean.Model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.util.Set;

@Entity
public class Assignee extends Model {

    private static final long serialVersionUID = 1L;

    @Id
    public Long id;

    @ManyToOne
    @Required
    public User user;

    @ManyToOne
    @Required
    public Project project;

    @OneToMany(mappedBy = "assignee")
    public Set<Issue> issues;

    public static final Finder<Long, Assignee> finder = new Finder<>(Assignee.class);

    public Assignee(Long userId, Long projectId) {
        user = User.find.byId(userId);
        project = Project.find.byId(projectId);
    }

    public static Assignee add(Long userId, Long projectId) {
        Assignee assignee = finder.query().where()
                .eq("user.id", userId).eq("project.id", projectId).findOne();
        if (assignee == null) {
            assignee = new Assignee(userId, projectId);
            assignee.save();
        }
        return assignee;
    }
}
