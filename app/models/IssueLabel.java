package models;

import java.util.List;

import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;

import models.enumeration.ResourceType;
import models.resource.Resource;
import play.data.validation.Constraints.Required;
import play.db.ebean.Model;

import javax.persistence.*;

import java.util.*;

@Entity
public class IssueLabel extends Model {

    /**
     *
     */
    private static final long serialVersionUID = -35487506476718498L;
    private static Finder<Long, IssueLabel> finder = new Finder<Long, IssueLabel>(Long.class, IssueLabel.class);

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

    public static List<IssueLabel> findByProjectId(Long projectId) {
        return finder.where().eq("project.id", projectId).findList();
    }

    public static IssueLabel findById(Long id) {
        return finder.byId(id);
    }

    @Transient
    public boolean exists() {
        return finder.where().eq("project.id", project.id)
                .eq("name", name).eq("color", color).findRowCount() > 0;
    }

    @Override
    public void delete() {
        for(Issue issue: issues) {
            issue.labels.remove(this);
            issue.save();
        }
        super.delete();
    }

    public Resource asResource() {
        return new Resource() {
            @Override
            public Long getId() {
                return id;
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