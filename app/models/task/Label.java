package models.task;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import play.db.ebean.Model;

@Entity
public class Label extends Model{
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
}
