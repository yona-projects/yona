package models;

import play.data.validation.Constraints;
import play.db.ebean.Model;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;

import models.enumeration.Direction;
import models.enumeration.Matching;
import models.support.FinderTemplate;
import models.support.OrderParams;
import models.support.SearchParams;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.Page;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Entity
public class User extends Model {
    private static final long serialVersionUID = 1L;
    private static Finder<Long, User> find = new Finder<Long, User>(Long.class,
            User.class);
    
    public static final int USER_COUNT_PER_PAGE = 30;
    
    @Id
    public Long id;
    
    public String name;
    
    @Constraints.Required
    public String loginId;
    
    @Constraints.Required
    public String password;
        
    @Constraints.Pattern("[0-9a-zA-Z]+(.[_a-z0-9-]+)*@(?:\\w+\\.)+\\w+$")
    public String email;
    
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
    
    public static User findProjectsById(Long id) {
        return find
                .fetch("projectUser.project","name")
                .where()
                    .eq("id", id)
                .findUnique();
    }

    public static User findByLoginId(String loginId) {
        return find.where().eq("loginId", loginId).findUnique();
    }

    public static boolean authenticate(User user) {
        User check = find.where().eq("loginId", user.loginId).findUnique();
        if(check == null) return false;
        if(check.password.equals(user.password)) return true;
        else return false;
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
    
    public static Page<User> findUsers(int pageNum, String loginId) {
        OrderParams orderParams = new OrderParams().add("loginId", Direction.ASC);
        SearchParams searchParams = new SearchParams();
        if(loginId != null) searchParams.add("loginId", loginId, Matching.CONTAINS);
        return FinderTemplate.getPage(orderParams, searchParams, find, USER_COUNT_PER_PAGE, pageNum);
    }
    
    /**
     * 해당 유저가 속해있는 프로젝트들 중에서 해당 유저가 유일한 Manager인 프로젝트가 있는지 검사하고, 
     * 있다면 그 프로젝트들의 리스트를 반환합니다.
     * 
     * @param userId
     * @return
     */
    public static List<Project> isOnlyManager(Long userId) {
        List<Project> projects = Ebean.find(Project.class)
                                        .select("id")
                                        .select("name")
                                        .where()
                                            .eq("projectUser.user.id", userId)
                                            .eq("projectUser.role.id", Role.MANAGER)
                                        .findList();
        for(Project project : projects) {
            if(ProjectUser.isManager(project.id))
                projects.remove(project);
        }
        return projects;
    }
}
