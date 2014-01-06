package models;

import models.enumeration.ResourceType;
import models.resource.Resource;
import models.resource.ResourceConvertible;
import play.data.validation.Constraints.Required;
import play.db.ebean.Model;

import javax.persistence.*;

import java.util.*;

@Entity
public class IssueLabel extends Model implements ResourceConvertible {

    /**
     *
     */
    private static final long serialVersionUID = -35487506476718498L;
    public static final Finder<Long, IssueLabel> finder = new Finder<>(Long.class, IssueLabel.class);

    @Id
    public Long id;

    @Required
    public String category;

    @Required
    public String color;

    @Required
    public String name;

    @ManyToOne
    public Project project;

    @ManyToMany(mappedBy="labels", fetch = FetchType.EAGER)
    public Set<Issue> issues;

    public static List<IssueLabel> findByProject(Project project) {
        return finder.where()
                .eq("project.id", project.id)
                .orderBy().asc("category")
                .orderBy().asc("id")
                .findList();
    }

    public String toString() {
        return category + " - " + name;
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
}
