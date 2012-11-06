package models.task;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ObjectNode;

import play.db.ebean.Model;
import play.libs.Json;

@Entity
public class Item extends Model {
	private static final long serialVersionUID = 1L;
	
	@Id
    public Long id;
    private boolean state;//체크 안체크
    public String body;
    
    @ManyToOne
    public Checklist checklist;
    
    public boolean isDone(){
        return state;
    }
    public void check(){
        state = true;
    }
    public void uncheck(){
        state = false;
    }
    
    public static Finder<Long, Item> find = new Finder<Long, Item>(Long.class, Item.class);
    
    public static Item findById(Long id) {
        return find.byId(id);
    }
    public JsonNode toJSON() {
        // TODO MAKETEST
        ObjectNode json = Json.newObject();
        json.put("state", state);
        json.put("body", body);
        return json;
    }
    public void acceptJSON(JsonNode json){
    	body = json.get("body").asText();
    	state = json.get("state").asBoolean();
    }
}
