package models.task;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.Id;

import play.db.ebean.Model;

@Entity
public class CheckList extends Model {
    @Id
    public Long id;
    public List<Item> items = new ArrayList<Item>();
    public String title;

    public static Finder<Long, CheckList> find = new Finder<Long, CheckList>(Long.class,
            CheckList.class);

    public static CheckList findById(Long id) {
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
}
