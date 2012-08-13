package models;

import com.avaje.ebean.Page;
import models.enumeration.Direction;
import models.enumeration.Matching;
import models.support.FinderTemplate;
import models.support.OrderParams;
import models.support.SearchParams;
import play.data.validation.Constraints;
import play.db.ebean.Model;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity
public class User extends Model {
    private static final long serialVersionUID = 1L;
    private static Finder<Long, User> find = new Finder<Long, User>(Long.class,
            User.class);

    public static final int USER_COUNT_PER_PAGE = 30;
    public static final Long SITE_MANAGER_ID = 1l;

    @Id
    public Long id;

    public String name;
    public String loginId;
    public String password;

    @Constraints.Pattern("[0-9a-zA-Z]+(.[_a-z0-9-]+)*@(?:\\w+\\.)+\\w+$")
    public String email;

    public String profileFilePath;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    public List<ProjectUser> projectUser;

    public String getName() {
        return this.name;
    }

    public static User findByName(String name) {
        return find.where().eq("name", name).findUnique();
    }

    public static User findById(Long id) {
        return find.byId(id);
    }

    public static User findProjectsById(Long id) {
        return find
                .fetch("projectUser.project", "name")
                .where()
                    .eq("id", id)
                .findUnique();
    }

    public static User findByLoginId(String loginId) {
        return find.where().eq("loginId", loginId).findUnique();
    }

    public static boolean authenticate(User user) {
        User check = findByLoginId(user.loginId);
        if(check == null){
            return false;
        }

        if(check.password.equals(user.password)){
            return true;
        } else {
            return false;
        }
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

    /**
     * Site manager를 제외한 사이트에 가입된 유저들의 리스트를 Page 형태로 반환합니다.
     *
     * @param pageNum
     * @param loginId
     * @return
     */
    public static Page<User> findUsers(int pageNum, String loginId) {
        OrderParams orderParams = new OrderParams().add("loginId", Direction.ASC);
        SearchParams searchParams = new SearchParams().add("id", 1l, Matching.NOT_EQUALS);

        if(loginId != null){
            searchParams.add("loginId", loginId, Matching.CONTAINS);
        }

        return FinderTemplate.getPage(orderParams, searchParams, find, USER_COUNT_PER_PAGE, pageNum);
    }

    /**
     * 해당 프로젝트에 속하는 유저들의 리스트를 제공합니다.
     * (Site manager는 hidden role로서 반환되지 않습니다.)
     *
     * @param projectId
     * @return
     */
    public static List<User> findUsersByProject(Long projectId) {
        return find.where()
                .eq("projectUser.project.id", projectId)
                .ne("projectUser.role.id", Role.SITEMANAGER).findList();
    }
}
