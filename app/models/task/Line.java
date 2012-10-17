package models.task;

import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import org.codehaus.jackson.JsonNode;


import play.db.ebean.Model;
import play.libs.Json;

@Entity
public class Line extends Model {
    @Id
    public Long id;
    public String title;
    public List<Card> cards;
    
    public JsonNode toJSON() {
        // TODO Auto-generated method stub
        return Json.newObject();
    }
}
