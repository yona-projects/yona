package models.task;

import javax.persistence.Entity;
import javax.persistence.Id;

import models.ProjectUser;
import play.db.ebean.Model;

@Entity
public class TaskComment extends Model {
    @Id
    public Long id;
    public String body;
    public ProjectUser author;
    
    private static Finder<Long, TaskComment> find = new Finder<Long, TaskComment>(Long.class, TaskComment.class);
    
    public static TaskComment findById(Long id) {
        return find.byId(id);
    }
}
