package models;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.*;

import models.enumeration.Direction;
import models.enumeration.Matching;
import models.enumeration.ResourceType;
import models.enumeration.RoleType;
import models.resource.Resource;
import models.support.FinderTemplate;
import models.support.OrderParams;
import models.support.SearchParams;
import org.apache.shiro.crypto.hash.Sha256Hash;
import org.apache.shiro.util.ByteSource;
import play.data.format.Formats;
import play.data.validation.Constraints.*;
import play.db.ebean.Model;
import utils.JodaDateUtil;

import com.avaje.ebean.Page;

import controllers.UserApp;

@Table(name = "n4user")
@Entity
public class User extends Model {
    private static final long serialVersionUID = 1L;
    public static Model.Finder<Long, User> find = new Finder<Long, User>(Long.class,
            User.class);

    public static final int USER_COUNT_PER_PAGE = 30;
    public static final Long SITE_MANAGER_ID = 1l;

    @Id
    public Long id;
    public String name;

    @Pattern(value = "^[a-zA-Z0-9-]+([_.][a-zA-Z0-9-]+)*$", message = "user.wrongloginId.alert") @Required
    public String loginId;
    
    @Transient
    public String oldPassword;
    public String password;
    public String passwordSalt;

    @Email(message = "user.wrongEmail.alert")
    public String email;
    public String avatarUrl;
    public boolean rememberMe;
    public boolean isLocked = false;

    @Formats.DateTime(pattern = "yyyy-MM-dd")
    public Date createdDate;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    public List<ProjectUser> projectUser;

    public User(){}

    public User(Long id){
        this.id = id;
    }
    /**
     * 완료일을 yyyy-MM-dd 형식의 문자열로 변환시킵니다.
     *
     * @return
     */
    public String getDateString() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd yyyy");
        return sdf.format(this.createdDate);
    }

    public List<Project> myProjects(){
        return Project.findProjectsByMember(id);
    }

    public static Long create(User user) {
    	user.createdDate = JodaDateUtil.now();
        user.save();
        return user.id;
    }

    public static User findByName(String name) {
        return find.where().eq("name", name).findUnique();
    }

    public static User findProjectsById(Long id) {
        return find.fetch("projectUser.project", "name").where().eq("id", id)
                .findUnique();
    }

    public static User findByLoginId(String loginId) {
        User user = find.where().eq("loginId", loginId).findUnique();
        if(  user == null ) {
            return UserApp.anonymous;
        } else {
            return user;
        }
    }

    public static User findByEmail(String email) {
        User user = find.where().eq("email", email).findUnique();
        if(  user == null ) {
            UserApp.anonymous.email = email;
            return UserApp.anonymous;
        } else {
            return user;
        }
    }
    /**
     * 존재하는 유저인지를 검사합니다.
     *
     * @param loginId
     * @return
     */
    public static boolean isLoginIdExist(String loginId) {
        int findRowCount = find.where().eq("loginId", loginId).findRowCount();
        return (findRowCount != 0);
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
        OrderParams orderParams = new OrderParams().add("loginId",
                Direction.ASC);
        SearchParams searchParams = new SearchParams().add("id", 1l,
                Matching.NOT_EQUALS);
        searchParams.add("loginId", UserApp.anonymous.loginId,
                Matching.NOT_EQUALS);

        if (loginId != null) {
            searchParams.add("loginId", loginId, Matching.CONTAINS);
        }

        return FinderTemplate.getPage(orderParams, searchParams, find,
                USER_COUNT_PER_PAGE, pageNum);
    }

    /**
     * 해당 프로젝트에 속하는 유저들의 리스트를 제공합니다. (Site manager는 hidden role로서 반환되지 않습니다.)
     *
     * @param projectId
     * @return
     */
    public static List<User> findUsersByProject(Long projectId) {
        return find.where().eq("projectUser.project.id", projectId)
                .ne("projectUser.role.id", RoleType.SITEMANAGER.roleType())
                .findList();
    }

    @Transient
    public Long avatarId(){
        return Attachment.findByContainer(ResourceType.USER_AVATAR, id).get(0).id;
    }

    public static boolean isEmailExist(String emailAddress) {
        User user = find.where().ieq("email", emailAddress).findUnique();
        return user != null;
    }

    public boolean isAnonymous() {
        return this == UserApp.anonymous;
    }

    public static void resetPassword(String loginId, String newPassword) {
        User user = findByLoginId(loginId);
        user.password = new Sha256Hash(newPassword,
                ByteSource.Util.bytes(user.passwordSalt), 1024).toBase64();
        user.save();
    }

    public Resource asResource() {
        return new Resource() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public Project getProject() {
                return null;
            }

            @Override
            public ResourceType getType() {
                return ResourceType.USER;
            }
        };
    }

    public boolean isSiteManager() {
        return SiteAdmin.exists(this);
    }
}
