package models;

import java.text.SimpleDateFormat;
import java.util.*;

import javax.persistence.*;

import controllers.UserApp;
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
import utils.ReservedWordsValidator;

import com.avaje.ebean.Page;

/**
 * User 클래스
 *
 *
 * @author WanSoon Park
 */
@Table(name = "n4user")
@Entity
public class User extends Model {
    private static final long serialVersionUID = 1L;
    public static final Model.Finder<Long, User> find = new Finder<>(Long.class, User.class);

    /**
     * 한 페이지에 보여줄 사용자 개수.
     */
    public static final int USER_COUNT_PER_PAGE = 30;
    /**
     * 사이트 관리자의 id값.
     */
    public static final Long SITE_MANAGER_ID = 1l;
    /**
     * 로그인ID 패턴
     */
    public static final String LOGIN_ID_PATTERN = "[a-zA-Z0-9-]+([_.][a-zA-Z0-9-]+)*";

    //TODO anonymous를 사용하는 것이아니라 향후 NullUser 패턴으로 usages들을 교체해야 함
    public static final User anonymous = new NullUser();

    /**
     * PK
     */
    @Id
    public Long id;
    /**
     * 화면에 보여줄 사용자 이름
     */
    public String name;

    /**
     * 로그인할 때 사용할 아이디
     */
    @Pattern(value = "^" + LOGIN_ID_PATTERN + "$", message = "user.wrongloginId.alert") @Required
    @ValidateWith(ReservedWordsValidator.class)
    public String loginId;
    /**
     * 비밀번호 수정할 때 기존 비밀번호 확인할 때 사용하는 값
     */
    @Transient
    public String oldPassword;
    /**
     * 비밀번호
     */
    public String password;
    /**
     * 비밀번호 암화할 할 때 사용하는 값
     */
    public String passwordSalt;

    /**
     * 이메일
     */
    @Email(message = "user.wrongEmail.alert")
    public String email;
    /**
     * 아바타 URL
     */
    public String avatarUrl;
    /**
     * 로그인 정보를 기억할지 나타내는 값
     */
    public boolean rememberMe;

    /**
     * 계정 잠금
     */
    public boolean isLocked = false;

    /**
     * 계정 생성일
     */
    @Formats.DateTime(pattern = "yyyy-MM-dd")
    public Date createdDate;

    /**
     * 프로젝트에서 사용자의 역할을 나타내는 값
     *
     * 해당 프로젝트의 관리자 혹은 멤버일 수 있다.
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    public List<ProjectUser> projectUser;

    /**
     * 관심 프로젝트
     */
    @ManyToMany
    @JoinTable(name = "user_watching_project",
            joinColumns= @JoinColumn(name="user_id"),
            inverseJoinColumns= @JoinColumn(name="project_id")
    )
    public List<Project> watchingProjects;

    public User(){}

    public User(Long id){
        this.id = id;
    }

    /**
     * 완료일을 yyyy-MM-dd 형식의 문자열로 변환한다.
     *
     * view에서 노출하기 위한 용도로 사용한다.
     *
     * @return
     */
    public String getDateString() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
        return sdf.format(this.createdDate);
    }

    /**
     * 자신이 속한 프로젝트 목록을 반환한다.
     *
     * @return
     */
    public List<Project> myProjects(String orderString){
        return Project.findProjectsByMemberWithFilter(id, orderString);
    }

    /**
     * 사용자를 추가한다.
     *
     * 사용자 추가시 생성일을 설정하고 PK를 반환한다.
     *
     * @param user
     * @return
     */
    public static Long create(User user) {
        user.createdDate = JodaDateUtil.now();
        user.save();
        return user.id;
    }

    /**
     * 로그인 아이디로 사용자를 조회한다.
     *
     * 사용자가 없으면 {@link #anonymous} 객체를 반환한다.
     *
     * @param loginId
     * @return
     */
    public static User findByLoginId(String loginId) {
        User user = find.where().eq("loginId", loginId).findUnique();
        if(  user == null ) {
            return anonymous;
        } else {
            return user;
        }
    }

    /**
     * email로 사용자를 조회한다.
     *
     * 사용자가 없으면 {@link #anonymous}객체에 email을 할당하고 반환한다.
     *
     * @param email
     * @return
     */
    public static User findByEmail(String email) {
        User user = find.where().eq("email", email).findUnique();
        if(  user == null ) {
            anonymous.email = email;
            return anonymous;
        } else {
            return user;
        }
    }
    /**
     * 로그인아이디로 존재하는 사용자인지를 확인한다.
     *
     * @param loginId
     * @return 사용자 존재여부
     */
    public static boolean isLoginIdExist(String loginId) {
        int findRowCount = find.where().eq("loginId", loginId).findRowCount();
        return (findRowCount != 0);
    }

    /**
     * 전체 사용자 PK와 이름을 반환한다.
     *
     * @return
     */
    public static Map<String, String> options() {
        LinkedHashMap<String, String> options = new LinkedHashMap<String, String>();
        for (User user : User.find.orderBy("name").findList()) {
            options.put(user.id.toString(), user.name);
        }
        return options;
    }

    /**
     * 익명사용자와 사이트 관리자를 제외한 사이트에 가입된 사용자 목록을 로그인 아이디로 정렬하여 Page객체로 반환한다.
     *
     * @param pageNum 해당 페이지
     * @param loginId {@code loginId}가 null이 아니면 {@code loginId}를 포함하고 있는 사용자 목록을 검색한다.
     * @return
     */
    public static Page<User> findUsers(int pageNum, String loginId) {
        OrderParams orderParams = new OrderParams().add("loginId",
                Direction.ASC);
        SearchParams searchParams = new SearchParams().add("id", 1l,
                Matching.NOT_EQUALS);
        searchParams.add("loginId", anonymous.loginId,
                Matching.NOT_EQUALS);

        if (loginId != null) {
            searchParams.add("loginId", loginId, Matching.CONTAINS);
        }

        return FinderTemplate.getPage(orderParams, searchParams, find,
                USER_COUNT_PER_PAGE, pageNum);
    }

    /**
     * 사이트 관리자를 제외한 특정 프로젝트에 속한 사용자 목록을 반환한다.
     *
     * @param projectId
     * @return
     */
    public static List<User> findUsersByProject(Long projectId) {
        return find.where().eq("projectUser.project.id", projectId)
                .ne("projectUser.role.id", RoleType.SITEMANAGER.roleType())
                .findList();
    }

    /**
     * 사용자의 아바타 아이디를 반환한다.
     * @return
     */
    @Transient
    public Long avatarId(){
        List<Attachment> attachments = Attachment.findByContainer(avatarAsResource());
        return attachments.get(attachments.size()-1).id;
    }

    /**
     * 기존에 존재하는 email인지 확인한다.
     *
     * @param emailAddress
     * @return email이 있으면 true / 없으면 false
     */
    public static boolean isEmailExist(String emailAddress) {
        User user = find.where().ieq("email", emailAddress).findUnique();
        return user != null;
    }

    /**
     * 사용자가 익명사용자인지 확인한다.
     * @return
     */
    public boolean isAnonymous() {
        return this.id.equals(anonymous.id);
    }

    /**
     * 로그인 아이디로 사용자를 조회하고 새 비밀번호를 암호화하여 설정한다.
     *
     * @param loginId
     * @param newPassword {@link User.passwordSalt}로 암호화하여 설정할 새 비밀번호
     */
    public static void resetPassword(String loginId, String newPassword) {
        User user = findByLoginId(loginId);
        user.password = new Sha256Hash(newPassword,
                ByteSource.Util.bytes(user.passwordSalt), 1024).toBase64();
        user.save();
    }

    /**
     * 모델을 리소스 객체로 반환한다.
     *
     * 권한검사와 첨부파일 정보를 포함한다.
     *
     * @return
     */
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

    public Resource avatarAsResource() {
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
                return ResourceType.USER_AVATAR;
            }
        };
    }

    /**
     * 사이트 관리자 여부를 확인한다.
     * @return
     */
    public boolean isSiteManager() {
        return SiteAdmin.exists(this);
    }

    public List<Project> getWatchingProjects(){
        if(this.watchingProjects == null) {
            this.watchingProjects = new ArrayList<>();
        }
        return this.watchingProjects;
    }

    public void addWatching(Project project) {
        getWatchingProjects().add(project);
        project.upWatcingCount();
        project.update();
    }

    public void removeWatching(Project project) {
        getWatchingProjects().remove(project);
        project.downWathcingCount();
        project.update();
    }

    public static boolean isWatching(Project project) {
        User user = UserApp.currentUser();
        if(user.isAnonymous()) {
            return false;
        }
        return user.getWatchingProjects().contains(project);
    }

}
