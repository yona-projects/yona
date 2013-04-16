package models;

import models.enumeration.ResourceType;
import models.resource.Resource;
import play.data.validation.Constraints.Required;
import play.db.ebean.Model;

import javax.persistence.*;
import java.util.List;
import java.util.Set;

@Entity
public class Tag extends Model {

    /**
     *
     */
    private static final long serialVersionUID = -35487506476718498L;
    public static Finder<Long, Tag> find = new Finder<Long, Tag>(Long.class, Tag.class);

    @Id
    public Long id;

    @Required
    @Column(unique=true)
    public String name;

    @ManyToMany(mappedBy="tags")
    public Set<Project> projects;

    public static List<Tag> findByProjectId(Long projectId) {
        return find.where().eq("project.id", projectId).findList();
    }

    public static Tag findById(Long id) {
        return find.byId(id);
    }

    @Transient
    public boolean exists() {
        return find.where().eq("name", name).findRowCount() > 0;
    }

    @Override
    public void delete() {
        for(Project project: projects) {
            project.tags.remove(this);
            project.save();
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
                return null;
            }

            @Override
            public ResourceType getType() {
                return ResourceType.TAG;
            }
        };
    }
}