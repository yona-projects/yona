package models.task;

import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;

import play.db.ebean.Model;
import play.libs.Json;

@Entity
public class Line extends Model {
	private static final long serialVersionUID = 1L;
	
    @Id
    public Long id;
    public String title;
    
    @OneToMany(mappedBy="line", cascade=CascadeType.ALL)
    public List<Card> cards;
    
    @ManyToOne
    public TaskBoard taskBoard;
    
    private static Finder<Long, Line> find = new Finder<Long, Line>(Long.class, Line.class);
    
    public JsonNode toJSON() {
        ObjectNode json = Json.newObject();
        json.put("_id", id);
        json.put("title", this.title);
        ArrayNode arr = Json.newObject().arrayNode();
        for(Card card : cards){
            arr.add(card.toJSON());
        }
        json.put("cards", arr);
        return json;
    }

    public static Line findById(Long id) {
        return find.byId(id);
    }

    public void accecptJSON(JsonNode json) {
        // TODO 한 라인에 정보들을 저장하면 된다.
        //존재하지 않는 카드는 더하고 없는 카드는 삭제한다.
        title = json.get("title").asText();
        cards.clear();
        JsonNode cardsJson = json.get("cards");
        for(int i = 0; i < cardsJson.size(); i++) {
            JsonNode cardJson = cardsJson.get(i);
            Long cardId = cardJson.get("_id").asLong();
            Card card = Card.findById(cardId);
            if(card == null){
                card = new Card();
            }
            cards.add(card);
            card.line = this;
            card.accecptJSON(cardJson);
            card.save();
        }
        save();//왜 save가 안되는거야?
        //아마 지워진걸 delete를 해줘야 할듯... 안하면 안되는듯...
    }

    public void addCard(Card card) {
        // TODO MAKETEST
        cards.add(card);
        save();
    }
}
