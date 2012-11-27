package models;

import java.util.List;

import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;

import play.data.validation.Constraints.Required;
import play.data.validation.*;
import play.db.ebean.Model;

import play.data.validation.*;
import play.db.ebean.*;

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

    public boolean exists() {
        return finder.where().eq("project.id", project.id)
                .eq("name", name).eq("color", color).findRowCount() > 0;
    }
}
