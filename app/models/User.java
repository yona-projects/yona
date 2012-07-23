package models;

import play.db.ebean.Model;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Entity
public class User extends Model {
    private static final long serialVersionUID = 1L;

    @Id
    public Long id;
    public String name;
    public String loginId;
    public String password;
    public String role;
    @OneToMany(mappedBy = "owner")
    public Set<Project> projects;
    @OneToMany(mappedBy = "author")
    public Set<Post> posts;
    @OneToMany(mappedBy = "author")
    public Set<Comment> comments;
    @OneToMany(mappedBy = "author")
    public Set<IssueComment> issueComments;
    @OneToMany(mappedBy = "reporter")
    public Set<Issue> reportedIssues;
    @OneToMany(mappedBy = "assignee")
    public Set<Issue> assignedIssues;

    private static Finder<Long, User> find = new Finder<Long, User>(Long.class,
        User.class);

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public static User findByName(String name) {
        return find.where().eq("name", name).findUnique();
    }

    public static User findById(Long id) {
        return find.byId(id);
    }

    public static User authenticate(User user) {
        return find.where().eq("loginId", user.loginId)
            .eq("password", user.password).findUnique();
    }

    public static String findNameById(long id) {
        return find.byId(id).name;
    }

    public static Map<String, String> options() {
        LinkedHashMap<String, String> options = new LinkedHashMap<String, String>();
        for (User user : User.find.orderBy("name").findList()) {
            options.put(user.id.toString(), user.name);
        }
        return options;
    }
}
