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
public class IssueLabel extends Model implements ResourceConvertible {

    static public class IssueLabelException extends Exception {
        private static final long serialVersionUID = 1L;
        public IssueLabelException(String s) {
            super(s);
        }
    }

    private static final long serialVersionUID = -35487506476718498L;
    public static final Finder<Long, IssueLabel> finder = new Finder<>(Long.class, IssueLabel.class);

    @Id
    public Long id;

    @Required
    @ManyToOne
    public IssueLabelCategory category;

    @Required(message="label.error.color.empty")
    public String color;

    @Required(message="label.error.labelName.empty")
    @Size(max=255, message="label.error.labelName.tooLongSize")
    public String name;

    @ManyToOne
    public Project project;

    @ManyToMany(mappedBy="labels", fetch = FetchType.EAGER)
    public Set<Issue> issues;

    public static List<IssueLabel> findByProject(Project project) {
        return finder.where()
                .eq("project.id", project.id)
                .orderBy().asc("category.name")
                .orderBy().asc("name")
                .findList();
    }

    public String toString() {
        return category.name + " - " + name;
    }

    @Transient
    public boolean exists() {
        return finder.where()
                .eq("project.id", project.id)
                .eq("category", category)
                .eq("name", name)
                .eq("color", color)
                .findRowCount() > 0;
    }

    @Override
    public void delete() {
        for(Issue issue: issues) {
            issue.labels.remove(this);
            issue.save();
        }
        super.delete();
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
                return ResourceType.ISSUE_LABEL;
            }
        };
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        if (!super.equals(object)) return false;

        IssueLabel that = (IssueLabel) object;

        if (!color.equals(that.color)) return false;
        if (!id.equals(that.id)) return false;
        if (!name.equals(that.name)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (id != null ? id.hashCode() : 0);
        result = 31 * result + color.hashCode();
        result = 31 * result + name.hashCode();
        return result;
    }
}
