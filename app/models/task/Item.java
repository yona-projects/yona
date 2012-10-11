package models.task;

import javax.persistence.Entity;
import javax.persistence.Id;

import play.db.ebean.Model;

@Entity
public class Item extends Model {
    @Id
    public Long id;
    private boolean state;//체크 안체크
    public String body;
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
}
