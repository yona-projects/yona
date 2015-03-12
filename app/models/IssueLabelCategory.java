/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
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

import models.enumeration.ResourceType;
import models.resource.Resource;
import models.resource.ResourceConvertible;
import play.data.validation.Constraints.Required;
import play.db.ebean.Model;

import javax.persistence.*;
import javax.validation.constraints.Size;
import java.util.List;
import java.util.Set;

@Entity
public class IssueLabelCategory extends Model implements ResourceConvertible {

    private static final long serialVersionUID = 1L;

    public static final Finder<Long, IssueLabelCategory> find = new Finder<>(Long.class, IssueLabelCategory.class);

    @Id
    public Long id;

    @Required
    @ManyToOne
    public Project project;

    @Required(message="label.error.categoryName.empty")
    @Size(max=255, message="label.error.categoryName.tooLongSize")
    public String name;

    @OneToMany(mappedBy="category", cascade = CascadeType.ALL)
    public Set<IssueLabel> labels;

    /**
     * Does this category disapproves an issue to have two or more labels of
     * the category?
     */
    public boolean isExclusive;

    @Transient
    public boolean exists() {
        return find.where()
                .eq("project.id", project.id)
                .eq("name", name)
                .findRowCount() > 0;
    }

    public static IssueLabelCategory findBy(IssueLabelCategory instance) {
        return find.where()
                .eq("project.id", instance.project.id)
                .eq("name", instance.name)
                .findUnique();
    }

    public static List<IssueLabelCategory> findByProject(Project project) {
        return find.where()
                .eq("project.id", project.id)
                .orderBy().asc("name")
                .findList();
    }

    @Override
    public Resource asResource() {
        return new Resource() {
            @Override
            public String getId() {
                return id.toString();
            }

            @Override
            public Project getProject() {
                return project;
            }

            @Override
            public ResourceType getType() {
                return ResourceType.ISSUE_LABEL_CATEGORY;
            }
        };
    }
}
