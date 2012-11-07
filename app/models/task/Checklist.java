package models.task;

import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;

import play.db.ebean.Model;
import play.libs.Json;

@Entity
public class Checklist extends Model {
    private static final long serialVersionUID = 1L;

    @Id
    public Long id;
    public String title;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "checklist")
    public List<Item> items;

    public static Finder<Long, Checklist> find = new Finder<Long, Checklist>(
            Long.class, Checklist.class);

    public static Checklist findById(Long id) {
        return find.byId(id);
    }

    public void addItem(Item item) {
        item.save();
        items.add(item);
    }

    public void removeItem(Item item) {
        items.remove(item);
        item.delete();
    }

    public JsonNode toJSON() {
        ObjectNode json = Json.newObject();
        json.put("title", title);
        ArrayNode itemsJson = Json.newObject().arrayNode();
        for (Item item : items) {
            itemsJson.add(item.toJSON());
        }
        json.put("items", itemsJson);
        return json;
    }

    public void acceptJSON(JsonNode json) {
        // TODO MAEKTEST
        title = json.get("title").asText();
        JsonNode itemsJson = json.get("items");
        for (Item item : items) {
            item.delete();
        }
        items.clear();
        for (int i = 0; i < itemsJson.size(); i++) {
            JsonNode itemJson = itemsJson.get(i);
            Item item = new Item();
            item.acceptJSON(itemJson);
            items.add(item);
        }
        save();
    }
}
