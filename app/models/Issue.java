package models;

import javax.persistence.Entity;
import javax.persistence.Id;

import play.db.ebean.Model;

@Entity
public class Issue extends Model {

    @Id
    private Long id;
    private String title;
    private String body;
    public static Finder<Long, Issue> find = new Finder<Long, Issue>(Long.class, Issue.class);

    public void setTitle(String title) {
        this.title = title;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public static void create(Issue issue) {
        issue.save();
    }

    public String getTitle() {
        return this.title;
    }

    public String getBody() {
        return this.body;
    }

    public Long getId() {
        return null;
    }

    public static Issue findById(Long id) {
        return find.ref(id);
    }


}
