package models.task;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import models.ProjectUser;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ObjectNode;

import play.db.ebean.Model;
import play.libs.Json;

@Entity
public class TaskComment extends Model {
	private static final long serialVersionUID = 1L;
	
    @Id
    public Long id;
    public String body;
    public ProjectUser author;
    
    @ManyToOne
    public Card card;
    
    private static Finder<Long, TaskComment> find = new Finder<Long, TaskComment>(Long.class, TaskComment.class);
    
    public static TaskComment findById(Long id) {
        return find.byId(id);
    }

    public JsonNode toJSON() {
        // TODO 테스트 추가 해야 함.
        ObjectNode json = Json.newObject();
        json.put("_id", id);
        json.put("body", body);
        return json;
    }
}
