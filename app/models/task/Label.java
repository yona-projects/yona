package models.task;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ObjectNode;

import play.db.ebean.Model;
import play.libs.Json;

@Entity
public class Label extends Model{
	private static final long serialVersionUID = 1L;
	
    @Id
    public Long id;
    public String name;
    public String color;//#ffffff
    
    @ManyToOne
    public TaskBoard taskBoard;
    
    private static Finder<Long, Label> find = new Finder<Long, Label>(Long.class, Label.class);
    
    public static Label findById(Long id) {
        return find.byId(id);//TODO 테스트 코드 추가해야 함.
    }

    public JsonNode toJSON() {
        //TODO TESTCODE 작성
        ObjectNode json = Json.newObject();
        json.put("name", name);
        json.put("color", color);
        return json;
    }
}
