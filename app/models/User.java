package models;

import play.db.ebean.Model;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;

import com.avaje.ebean.Page;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Entity
public class User extends Model {
    private static final long serialVersionUID = 1L;
    private static Finder<Long, User> find = new Finder<Long, User>(Long.class,
            User.class);
    
    @Id
    public Long id;
    public String name;
    public String loginId;
    public String password;
    public String profileFilePath;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    public Set<ProjectUser> projectUser;

    public String getName() {
        return this.name;
    }

    public static User findByName(String name) {
        return find.where().eq("name", name).findUnique();
    }

    public static User findById(Long id) {
        return find.byId(id);
    }

    public static User findByLoginId(String loginId) {
        return find.where().eq("loginId", loginId).findUnique();
    }

    public static User authenticate(User user) {
        return find.where().eq("loginId", user.loginId)
                .eq("password", user.password).findUnique();
    }

    public static String findNameById(long id) {
        return find.byId(id).name;
    }

    public static String findLoginIdById(long id) {
        return find.byId(id).loginId;
    }

    public static Map<String, String> options() {
        LinkedHashMap<String, String> options = new LinkedHashMap<String, String>();
        for (User user : User.find.orderBy("name").findList()) {
            options.put(user.id.toString(), user.name);
        }
        return options;
    }
    
    public static Page<User> findUsers(int pageNum, String key, String order, int pageSize) {
        return find
                .orderBy(key + " " + order)
                .findPagingList(pageSize)
                .getPage(pageNum - 1);
    }
}
