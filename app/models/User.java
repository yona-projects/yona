package models;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.persistence.Entity;
import javax.persistence.Id;

import play.db.ebean.Model;

@Entity
public class User extends Model {
    private static final long serialVersionUID = 1L;

    @Id
    public Long id;
    public String name;
    public String loginId;
    public String password;
    public String role;

    private static Finder<Long, User> find = new Finder<Long, User>(Long.class,
            User.class);

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
