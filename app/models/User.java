/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/
package models;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.Page;
import com.avaje.ebean.RawSqlBuilder;
import controllers.UserApp;
import models.enumeration.ResourceType;
import models.enumeration.RoleType;
import models.enumeration.UserState;
import models.resource.GlobalResource;
import models.resource.Resource;
import models.resource.ResourceConvertible;
import models.support.UserComparator;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.crypto.hash.Sha256Hash;
import org.apache.shiro.util.ByteSource;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.data.validation.Constraints.Pattern;
import play.data.validation.Constraints.Required;
import play.data.validation.Constraints.ValidateWith;
import play.db.ebean.Model;
import play.db.ebean.Transactional;
import play.i18n.Messages;
import play.mvc.Http;
import utils.CacheStore;
import utils.GravatarUtil;
import utils.JodaDateUtil;
import utils.ReservedWordsValidator;

import javax.annotation.Nonnull;
import javax.persistence.*;
import java.text.SimpleDateFormat;
import java.util.*;

import static utils.HtmlUtil.defaultSanitize;

@Table(name = "n4user")
@Entity
public class User extends Model implements ResourceConvertible {
    private static final long serialVersionUID = 1L;

    public static final Model.Finder<Long, User> find = new Finder<>(Long.class, User.class);

    public static final Comparator<User> USER_NAME_COMPARATOR = new Comparator<User>() {
        @Override
        public int compare(User u1, User u2) {
            return u1.name.compareTo(u2.name);
        }
    };

    /**
     * Max number of user size to show per page at site admin user list page
     */
    public static final int USER_COUNT_PER_PAGE = 30;

    public static final Long SITE_MANAGER_ID = 1l;

    public static final String LOGIN_ID_PATTERN = "[a-zA-Z0-9가-힣-]+([_.][a-z_.A-Z0-9가-힣-]+)*";
    public static final String LOGIN_ID_PATTERN_ALLOW_FORWARD_SLASH = "[a-zA-Z0-9-/]+([_.][a-z_.A-Z0-9-/]+)*";

    public static final User anonymous = new NullUser();

    @Id
    public Long id;

    /**
     * name to show at web pages
     */
    public String name;
    public String englishName;

    @Pattern(value = "^" + LOGIN_ID_PATTERN + "$", message = "user.wrongloginId.alert")
    @Required
    @ValidateWith(value = ReservedWordsValidator.class, message = "validation.reservedWord")
    public String loginId;

    /**
     * only used for password reset
     */
    @Transient
    public String oldPassword;
    public String password;
    public String passwordSalt;
    @Constraints.Email(message = "user.wrongEmail.alert")
    public String email;
    public String token;

    @Transient
    private Boolean siteManager;

    @Transient
    private Map<Long, Boolean> projectManagerMemo = new HashMap<>();

    @Transient
    private Map<Long, Boolean> projectMembersMemo = new HashMap<>();

    @Transient
    private Map<String, Boolean> orgMembersMemo = new HashMap<>();

    /**
     * used for enabling remember me feature
     *
     * remember me = keep a use logged in
     */
    public boolean rememberMe;

    @Enumerated(EnumType.STRING)
    public UserState state;

    @Formats.DateTime(pattern = "yyyy-MM-dd")
    public Date lastStateModifiedDate;

    /**
     * account creation date
     */
    @Formats.DateTime(pattern = "yyyy-MM-dd")
    public Date createdDate;

    /**
     * user auth role of project
     *
     * It can be a project admin or member
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    public List<ProjectUser> projectUser;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    public List<OrganizationUser> groupUser;

    @OneToMany(mappedBy = "user")
    public List<FavoriteIssue> favoriteIssues;

    /**
     * project which is requested member join
     */
    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(name = "user_enrolled_project", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "project_id"))
    public List<Project> enrolledProjects;

    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(name = "user_enrolled_organization", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "organization_id"))
    public List<Organization> enrolledOrganizations;

    @ManyToMany(mappedBy = "receivers")
    @OrderBy("created DESC")
    public List<NotificationEvent> notificationEvents;

    /**
     * alternate user email addresses
     *
     * It is used to recognize as same user when a user uses multiple emails.
     *
     * cf. {@link #email} is regarded as a user's representative email
     *
     * {@link #email} can be set as one of {@link #emails}.
     * In that case, selected {@link #email} is removed from {@link #emails}
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    public List<Email> emails;

    @OneToMany(mappedBy = "user")
    public List<Mention> mentions;

    @OneToMany(mappedBy = "user")
    public List<FavoriteProject> favoriteProjects;

    @OneToMany(mappedBy = "user")
    public List<FavoriteOrganization> favoriteOrganizations;

    /**
     * The user's preferred language code which can be recognized by {@link play.api.i18n.Lang#get},
     * such as "ko", "en-US" or "ja". This field is used as a language for notification mail.
     */
    public String lang;

    @Transient
    public Long lastVisitedProjectId;

    @Transient
    public Long lastVisitedIssueId;


    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    public List<OrganizationUser> organizationUsers;

    public boolean isGuest = false;

    public User() {
    }

    public User(Long id) {
        this.id = id;
    }

    public boolean isMemberOf(Organization organization) {
        return isMemberOf(organization, RoleType.ORG_MEMBER);
    }

    public boolean isAdminOf(Organization organization) {
        return isMemberOf(organization, RoleType.ORG_ADMIN);
    }

    public boolean isMemberOf(Organization org, RoleType roleType) {
        if (org == null) {
            return false;
        }

        String key = org.id + ":" + roleType.toString();

        Boolean value = orgMembersMemo.get(key);

        if (value == null) {
            int rowCount = OrganizationUser.find.where().eq("organization.id", org.id)
                    .eq("user.id", id)
                    .eq("role.id", Role.findByRoleType(roleType).id)
                    .findRowCount();
            value = rowCount > 0;
            orgMembersMemo.put(key, value);
        }

        return value;
    }

    public String getPreferredLanguage() {
        if (lang != null) {
            return lang;
        } else {
            return Locale.getDefault().getLanguage();
        }
    }

    /**
     * User creation date which forms of "MMM dd, yyyy"
     *
     * It is made for view pages
     *
     * @return
     */
    public String getDateString() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
        return sdf.format(this.createdDate);
    }

    public List<Project> myProjects(String orderString) {
        return Project.findProjectsByMemberWithFilter(id, orderString);
    }

    public List<Project> ownProjects() {
        return Project.findByOwner(loginId);
    }

    /**
     * Create a user and set creation date
     *
     * @param user
     * @return user's id (not login id)
     */
    public static Long create(User user) {
        user.createdDate = JodaDateUtil.now();
        user.name = defaultSanitize(user.name);
        user.save();
        CacheStore.yonaUsers.put(user.id, user);
        return user.id;
    }

    /**
     * find a user by login id string
     *
     * If there is no user correspond to login id string,
     * then return {@link #anonymous} not null
     *
     * @param loginId
     * @return User or {@link #anonymous}
     */
    public static User findByLoginId(String loginId) {
        User user = find.where().eq("loginId", loginId).findUnique();
        if (user == null) {
            return anonymous;
        }
        else {
            return user;
        }
    }

    public static User findByUserToken(String token){
        User user = null;
        if(token != null) {
            user = User.find.where().eq("token", token).findUnique();
        }

        if(user != null){
            return user;
        }

        return User.anonymous;
    }

    public static User findUserIfTokenExist(User user){
        if(!user.isAnonymous()){
            return user;
        }

        String userToken = extractUserTokenFromRequestHeader(Http.Context.current().request());
        if( userToken != null) {
            return User.findByUserToken(userToken);
        }
        return User.anonymous;
    }

    public static String extractUserTokenFromRequestHeader(Http.Request request) {
        String authHeader = request.getHeader("Authorization");
        if(authHeader != null &&
                authHeader.contains("token ")) {
            return authHeader.split("token ")[1];
        }
        return request.getHeader(UserApp.USER_TOKEN_HEADER);
    }

    /**
     *
     * Find a user by email account.
     * - find a user with a given email account or who has the email account  as one of sub email accounts.
     * - If no user matched up with the given email account, then return new {@link models.NullUser}
     * after setting the email account to the object.
     *
     * @param email
     * @return
     */
    public static User findByEmail(String email) {
        User user = find.where().eq("email", email).findUnique();
        if (user != null) {
            return user;
        }

        Email subEmail = Email.findByEmail(email, true);
        if (subEmail != null) {
            return subEmail.user;
        }

        User fallback = find.where().ieq("email", email).findUnique();
        if (fallback != null) {
            return fallback;
        }

        User anonymous = new NullUser();
        anonymous.email = email;
        return anonymous;
    }

    public static User findByLoginKey(String loginIdOrEmail) {
        User user = find.where().ieq("loginId", loginIdOrEmail).findUnique();

        if (user == null) {
            user = find.where().eq("email", loginIdOrEmail).findUnique();
        }

        return (user == null) ? anonymous : user;
    }

    /**
     * check is existed user
     *
     * @param loginId
     * @return boolean
     */
    public static boolean isLoginIdExist(String loginId) {
        int findRowCount = find.where().ieq("loginId", loginId).findRowCount();
        return (findRowCount != 0);
    }

    /**
     * all user list mapped by id and name
     *
     */
    public static Map<String, String> options() {
        LinkedHashMap<String, String> options = new LinkedHashMap<>();
        for (User user : User.find.orderBy("name").findList()) {
            options.put(user.id.toString(), user.name);
        }
        return options;
    }

    /**
     *
     * All user list except anonymous and site admin
     *
     * @param pageNum
     * @param query If {@code query}is not null, search list contains {@code query}
     * @return user list forms of Page which is ordered by login id
     */
    public static Page<User> findUsers(int pageNum, String query, UserState state) {
        ExpressionList<User> el = User.find.where();
        el.ne("id",SITE_MANAGER_ID);
        el.ne("loginId",anonymous.loginId);
        if( state == UserState.GUEST ) {
            el.eq("isGuest", true);
        } else if (state == UserState.SITE_ADMIN) {
            el.in("id", getAdminUserIds());
        } else {
            el.eq("state", state);
        }

        if(StringUtils.isNotBlank(query)) {
            el = el.disjunction();
            el = el.icontains("loginId", query)
                    .icontains("name", query)
                    .icontains("englishName", query)
                    .icontains("email", query);
            el.endJunction();
        }

        return el.order().desc("createdDate").findPagingList(USER_COUNT_PER_PAGE).getPage(pageNum);
    }

    private static Set<Long> getAdminUserIds() {
        List<SiteAdmin> admins = SiteAdmin.find.all();
        Set<Long> adminUserIds = new HashSet<>();

        for(SiteAdmin admin: admins){
            if (admin.id != SITE_MANAGER_ID) {
                adminUserIds.add(admin.admin.id);
            }
        }
        return adminUserIds;
    }

    /**
     * project user list except site admin
     *
     * @param projectId
     * @return project admin and member list
     */
    public static List<User> findUsersByProject(Long projectId) {
        return find.where().eq("projectUser.project.id", projectId)
                .ne("projectUser.role.id", RoleType.SITEMANAGER.roleType()).orderBy().asc("name")
                .findList();
    }

    public static List<User> findUsersByProjectAndOrganization(Project project) {
        // member of this project.
        Set<Long> userIds = new HashSet<>();

        List<ProjectUser> pus = project.members();
        for(ProjectUser pu : pus) {
            userIds.add(pu.user.id);
        }

        // member of the group
        if(project.hasGroup()) {
            List<OrganizationUser> ous = null;
            if(project.isPrivate()) {
                ous = project.organization.getAdmins();
            } else {
                ous = OrganizationUser.find.fetch("user")
                        .where().eq("organization", project.organization).findList();
            }

            for(OrganizationUser ou : ous) {
                userIds.add(ou.user.id);
            }
        }

        if (UserApp.currentUser().isSiteManager()) {
            userIds.add(UserApp.currentUser().id);
        }

        List<User> users = find.where().in("id", userIds).orderBy().asc("name").findList();

        return users;
    }

    @Transient
    public Long avatarId() {
        List<Attachment> attachments = Attachment.findByContainer(avatarAsResource());
        if (attachments.size() > 0) {
            return attachments.get(attachments.size() - 1).id;
        }
        else {
            return null;
        }
    }

    /**
     * Check if email exists or not
     *
     * Also check the email exist in {@link Email} as a certificated side email
     *
     * @param emailAddress
     * @return boolean
     */
    public static boolean isEmailExist(String emailAddress) {
        User user = find.where().ieq("email", emailAddress).findUnique();
        return user != null || Email.exists(emailAddress, true);
    }

    /**
     * check whether this is a anonymous
     */
    public boolean isAnonymous() {
        return id == null || id.equals(anonymous.id);
    }

    /**
     * reset user password with a new password
     *
     * When new password save, it will be encryped with {@link User.passwordSalt}
     *
     * @param loginId
     * @param newPassword
     */
    public static void resetPassword(String loginId, String newPassword) {
        User user = findByLoginId(loginId);
        user.password = getHashedStringForPassword(newPassword, user.passwordSalt);
        CacheStore.yonaUsers.put(user.id, user);
        user.save();
    }

    public boolean isSamePassword(String newPassword) {
        return this.password.equals(getHashedStringForPassword(newPassword, this.passwordSalt));
    }

    private static String getHashedStringForPassword(String newPassword, String salt) {
        return new Sha256Hash(newPassword, ByteSource.Util.bytes(salt), 1024)
                .toBase64();
    }

    @Override
    public Resource asResource() {
        return new GlobalResource() {
            @Override
            public String getId() {
                return id.toString();
            }

            @Override
            public ResourceType getType() {
                return ResourceType.USER;
            }
        };
    }

    public Resource avatarAsResource() {
        return new GlobalResource() {
            @Override
            public String getId() {
                return id.toString();
            }

            @Override
            public ResourceType getType() {
                return ResourceType.USER_AVATAR;
            }
        };
    }

    public boolean isSiteManager() {
        if (siteManager == null) {
            siteManager = SiteAdmin.exists(this);
        }

        return siteManager;
    }

    public boolean isManagerOf(Project project) {
        if (!projectManagerMemo.containsKey(project.id)) {
            projectManagerMemo.put(project.id, ProjectUser.isManager(id, project.id));
        }

        return projectManagerMemo.get(project.id);
    }

    public boolean isMemberOf(Project project) {
        // TODO: Performance! Removed cache. If performance problem is occurred, fix it!
        return ProjectUser.isMember(id, project.id);
    }

    public List<Project> getEnrolledProjects() {
        if (this.enrolledProjects == null) {
            this.enrolledProjects = new ArrayList<>();
        }
        return this.enrolledProjects;
    }

    public List<Organization> getEnrolledOrganizations() {
        if (this.enrolledOrganizations == null) {
            this.enrolledOrganizations = new ArrayList<>();
        }
        return this.enrolledOrganizations;
    }

    @Transactional
    public void addWatching(Project project) {
        Watch.watch(this, project.asResource());
    }

    @Transactional
    public void removeWatching(Project project) {
        Watch.unwatch(this, project.asResource());
    }

    public static boolean isWatching(Project project) {
        return Watch.isWatching(project.asResource());
    }

    public List<Project> getWatchingProjects() {
        return getWatchingProjects(null);
    }

    public List<Project> getWatchingProjects(String orderString) {
        List<String> projectIds = Watch.findWatchedResourceIds(this, ResourceType.PROJECT);
        List<Project> projects = new ArrayList<>();
        for (String id : projectIds) {
            projects.add(Project.find.byId(Long.valueOf(id)));
        }
        if (StringUtils.isBlank(orderString)) {
            return projects;
        }
        return Ebean.filter(Project.class).sort(orderString).filter(projects);
    }

    /**
     * request join as a member at {@code project}
     *
     * @param project
     */
    public void enroll(Project project) {
        getEnrolledProjects().add(project);
        this.update();
        CacheStore.yonaUsers.put(this.id, this);
    }

    public void enroll(Organization organization) {
        getEnrolledOrganizations().add(organization);
        this.update();
        CacheStore.yonaUsers.put(this.id, this);
    }

    /**
     * cancel enrol request at {@code project}
     *
     * @param project
     */
    public void cancelEnroll(Project project) {
        getEnrolledProjects().remove(project);
        this.update();
        CacheStore.yonaUsers.put(this.id, this);
    }

    public void cancelEnroll(Organization organization) {
        getEnrolledOrganizations().remove(organization);
        this.update();
        CacheStore.yonaUsers.put(this.id, this);
    }

    /**
     * check current logged in user already sent a enrol request to {@code project}
     *
     * @param project
     * @return
     */
    public static boolean enrolled(Project project) {
        User user = UserApp.currentUser();
        if (user.isAnonymous()) {
            return false;
        }
        return user.getEnrolledProjects().contains(project);
    }

    public static boolean enrolled(Organization organization) {
        User user = UserApp.currentUser();
        if (user.isAnonymous()) {
            return false;
        }
        return user.getEnrolledOrganizations().contains(organization);
    }

    @Override
    public void delete() {
        for (Assignee assignee : Assignee.finder.where().eq("user.id", id).findList()) {
            assignee.delete();
        }
        CacheStore.yonaUsers.invalidate(this.id);
        super.delete();
    }

    public void changeState(UserState state) {
        refresh();
        this.state = state;
        lastStateModifiedDate = new Date();

        if (this.state == UserState.DELETED) {
            name = "[DELETED]" + this.name;
            oldPassword = "";
            password = "";
            passwordSalt = "";
            email = "deleted-" + loginId + "@noreply.yona.io";
            rememberMe = false;
            projectUser.clear();
            enrolledProjects.clear();
            notificationEvents.clear();
            for (Assignee assignee : Assignee.finder.where().eq("user.id", id).findList()) {
                for (Issue issue : assignee.issues) {
                    issue.assignee = null;
                    issue.update();
                }
                assignee.delete();
            }
        }

        update();
        CacheStore.yonaUsers.put(this.id, this);
    }

    public String avatarUrl() {
        Long avatarId = avatarId();
        if (avatarId == null) {
            return GravatarUtil.getAvatar(email, 64);
        }
        else {
            return controllers.routes.AttachmentApp.getFile(avatarId).url();
        }
    }

    public String avatarUrl(int size) {
        Long avatarId = avatarId();
        if (avatarId == null) {
            return GravatarUtil.getAvatar(email, size);
        }
        else {
            return controllers.routes.AttachmentApp.getFile(avatarId).url();
        }
    }

    /**
     * Find issue authors and {@code currentUser}
     *
     * Issue authors are found in a project whose id is {@code projectId}.
     *
     * @param currentUser
     * @param projectId
     * @return
     */
    public static List<User> findIssueAuthorsByProjectIdAndMe(User currentUser, long projectId) {
        String sql = "SELECT DISTINCT t0.id AS id, t0.name AS name, t0.login_id AS loginId " +
                "FROM n4user t0 JOIN issue t1 ON t0.id = t1.author_id";
        List<User> users = find.setRawSql(RawSqlBuilder.parse(sql).create()).where()
                .eq("t1.project_id", projectId)
                .orderBy().asc("t0.name")
                .findList();

        if (!users.contains(currentUser) && currentUser != User.anonymous) {
            users.add(currentUser);
            Collections.sort(users, new UserComparator());
        }

        return users;
    }

    /**
     * Find assigned users at a project whose id is {@code projectId}
     *
     * @param projectId
     * @return
     */
    public static List<User> findIssueAssigneeByProjectIdAndMe(User currentUser, long projectId) {
        String sql = "SELECT DISTINCT t0.id AS id, t0.name AS name, t0.login_id AS loginId " +
                "FROM n4user t0 JOIN assignee t1 ON t0.id = t1.user_id";
        List<User> users = find.setRawSql(RawSqlBuilder.parse(sql).create()).where()
                .eq("t1.project_id", projectId)
                .orderBy().asc("t0.name")
                .findList();

        if (!users.contains(currentUser) && currentUser != User.anonymous) {
            users.add(currentUser);
            Collections.sort(users, new UserComparator());
        }

        return users;
    }

    /**
     * All user list sent pull-requests at project whose id is {@code projectId}
     *
     * @param projectId
     * @return
     */
    public static List<User> findPullRequestContributorsByProjectId(long projectId) {
        String sql = "SELECT DISTINCT t0.id AS id, t0.name AS name, t0.login_id AS loginId " +
                "FROM n4user t0 JOIN pull_request t1 ON t0.id = t1.contributor_id";
        return find.setRawSql(RawSqlBuilder.parse(sql).create()).where()
                .eq("t1.to_project_id", projectId)
                .orderBy().asc("t0.name")
                .findList();
    }

    /**
     * find users at a project whose id is {@code projectId} and role is {@code roleType}
     *
     * @param projectId
     * @param roleType
     * @return
     */
    public static List<User> findUsersByProject(Long projectId, RoleType roleType) {
        return find.where().eq("projectUser.project.id", projectId)
                .eq("projectUser.role.id", roleType.roleType()).orderBy().asc("name").findList();
    }

    public static List<User> findUsersByOrganization(Long organizationId, RoleType roleType) {
        return find.where().eq("organizationUsers.organization.id", organizationId)
                .eq("organizationUsers.role.id", roleType.roleType()).orderBy().asc("name").findList();
    }

    /**
     * add to user's alternate email list
     *
     * @param email
     */
    public void addEmail(Email email) {
        email.save();
        emails.add(email);
    }

    /**
     * check {@code newEmail} exists in user's alternate email list
     *
     * @param newEmail
     * @return boolean
     */
    public boolean has(String newEmail) {
        for (Email email : emails) {
            if (email.email.equals(newEmail)) {
                return true;
            }
        }
        return false;
    }

    /**
     * remove {@code email} from user's altenate emails
     * @param email
     */
    public void removeEmail(Email email) {
        emails.remove(email);
        email.delete();
        CacheStore.yonaUsers.put(this.id, this);
    }

    public void visits(Project project) {
        if (this.isAnonymous()) return;
        if(!Objects.equals(project.id, this.lastVisitedProjectId)){
            this.lastVisitedProjectId = project.id;
            RecentProject.addNew(this, project);
        }
    }

    public void visits(Issue issue) {
        if (this.isAnonymous()) return;

        if(!Objects.equals(issue.id, this.lastVisitedIssueId)){
            this.lastVisitedProjectId =issue.id;
            RecentIssue.addNewIssue(this, issue);
        }
    }

    public void visits(Posting posting) {
        if (this.isAnonymous()) return;

        if(!Objects.equals(posting.id, this.lastVisitedIssueId)){
            this.lastVisitedProjectId =posting.id;
            RecentIssue.addNewPosting(this, posting);
        }
    }

    public List<Project> getVisitedProjects() {
        List<Project> projects = RecentProject.getRecentProjects(this);
        if(projects == null || projects.size() == 0){
            return new ArrayList<>();
        }

        return projects;
    }

    public List<RecentIssue> getVisitedIssues() {
        List<RecentIssue> issues = RecentIssue.getRecentIssues(this);
        if(issues == null || issues.size() == 0){
            return new ArrayList<>();
        }

        return issues;
    }

    public List<Organization> getOrganizations(int size) {
        if(size < 1) {
            throw new IllegalArgumentException("the size should be bigger then 0");
        }
        List<Organization> orgs = new ArrayList<>();
        for(OrganizationUser ou : OrganizationUser.findByUser(this, size)) {
            orgs.add(ou.organization);
        }
        return orgs;
    }

    public void createOrganization(Organization organization) {
        OrganizationUser ou = new OrganizationUser();
        ou.user = this;
        ou.organization = organization;
        ou.role = Role.findByRoleType(RoleType.ORG_ADMIN);
        ou.save();

        this.add(ou);
        organization.add(ou);
        this.update();
        CacheStore.yonaUsers.put(this.id, this);
    }

    private void add(OrganizationUser ou) {
        this.organizationUsers.add(ou);
    }

    public String toString() {
        if (isAnonymous()) {
            return Messages.get("user.role.anonymous");
        } else {
            return name + "(" + loginId + ")";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        User user = (User) o;

        return !(id != null ? !id.equals(user.id) : user.id != null);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    public List<Project> getFavoriteProjects() {
        final User user = this;
        favoriteProjects.sort(new Comparator<FavoriteProject>() {
            @Override
            public int compare(FavoriteProject o1, FavoriteProject o2) {
                if (o1.owner.equals(user.loginId) || o2.owner.equals(user.loginId)) {
                    return Integer.MIN_VALUE;
                }
                if (o1.owner.equals(o2.owner)) {
                    return o1.projectName.compareToIgnoreCase(o2.projectName);
                }
                return o1.owner.compareToIgnoreCase(o2.owner);
            }
        });

        List<Project> projects = new ArrayList<>();
        for (FavoriteProject favoriteProject : this.favoriteProjects) {
            favoriteProject.project.refresh();
            projects.add(favoriteProject.project);
        }

        return projects;
    }

    public void updateFavoriteProject(@Nonnull Project project){
        for (FavoriteProject favoriteProject : this.favoriteProjects) {
            if (favoriteProject.project.id.equals(project.id)) {
                favoriteProject.project.refresh();
            }
        }
    }

    public void updateFavoriteOrganization(@Nonnull Organization organization){
        for (FavoriteOrganization favoriteOrganization : this.favoriteOrganizations) {
            if (favoriteOrganization.organization.id.equals(organization.id)) {
                favoriteOrganization.organization.refresh();
            }
        }
    }

    public boolean toggleFavoriteProject(Long projectId) {
        for (FavoriteProject favoriteProject : this.favoriteProjects) {
            if( favoriteProject.project.id.equals(projectId) ){
                removeFavoriteProject(projectId);
                this.favoriteProjects.remove(favoriteProject);
                RecentProject.deletePrevious(this, favoriteProject.project);
                return false;
            }
        }

        FavoriteProject favoriteProject = new FavoriteProject(this, Project.find.byId(projectId));
        this.favoriteProjects.add(favoriteProject);
        favoriteProject.save();
        return true;
    }

    public void removeFavoriteProject(Long projectId) {
        List<FavoriteProject> list = FavoriteProject.finder.where()
                .eq("user.id", this.id)
                .eq("project.id", projectId).findList();

        if(list != null && list.size() > 0){
            favoriteProjects.remove(list.get(0));
            list.get(0).delete();
        }
    }

    public List<Organization> getFavoriteOrganizations() {
        List<Organization> organizations = new ArrayList<>();
        for (FavoriteOrganization favoriteOrganization : this.favoriteOrganizations) {
            favoriteOrganization.organization.refresh();
            organizations.add(0, favoriteOrganization.organization);
        }

        return organizations;
    }

    public boolean toggleFavoriteOrganization(Long organizationId) {
        for (FavoriteOrganization favoriteOrganization : this.favoriteOrganizations) {
            if( favoriteOrganization.organization.id.equals(organizationId) ){
                removeFavoriteOrganization(organizationId);
                this.favoriteOrganizations.remove(favoriteOrganization);
                return false;
            }
        }

        FavoriteOrganization favoriteOrganization = new FavoriteOrganization(this, Organization.find.byId(organizationId));
        this.favoriteOrganizations.add(favoriteOrganization);
        favoriteOrganization.save();
        return true;
    }

    private void removeFavoriteOrganization(Long organizationId) {
        List<FavoriteOrganization> list = FavoriteOrganization.finder.where()
                .eq("user.id", this.id)
                .eq("organization.id", organizationId).findList();

        if(list != null && list.size() > 0){
            favoriteOrganizations.remove(list.get(0));
            list.get(0).delete();
        }
    }

    public List<Issue> getFavoriteIssues() {
        List<Issue> issues = new ArrayList<>();
        for (FavoriteIssue favoriteIssue : this.favoriteIssues) {
            favoriteIssue.issue.refresh();
            issues.add(0, favoriteIssue.issue);
        }

        return issues;
    }

    public void updateFavoriteIssue(@Nonnull Issue issue){
        for (FavoriteIssue favoriteIssue : this.favoriteIssues) {
            if (favoriteIssue.issue.id.equals(issue.id)) {
                favoriteIssue.issue.refresh();
            }
        }
    }

    public boolean toggleFavoriteIssue(Long issueId) {
        for (FavoriteIssue favoriteIssue : this.favoriteIssues) {
            if( favoriteIssue.issue.id.equals(issueId) ){
                removeFavoriteIssue(issueId);
                this.favoriteIssues.remove(favoriteIssue);
                return false;
            }
        }

        FavoriteIssue favoriteIssue = new FavoriteIssue(this, Issue.finder.byId(issueId));
        this.favoriteIssues.add(favoriteIssue);
        favoriteIssue.save();
        return true;
    }

    public void removeFavoriteIssue(Long issueId) {
        List<FavoriteIssue> list = FavoriteIssue.find.where()
                .eq("user.id", this.id)
                .eq("issue.id", issueId).findList();

        if(list != null && list.size() > 0){
            favoriteIssues.remove(list.get(0));
            list.get(0).delete();
        }
    }

    public List<Project> getIssueMovableProject(){
        Set<Project> projects = new LinkedHashSet<>();
        projects.addAll(getFavoriteProjects());
        projects.addAll(getVisitedProjects());
        projects.addAll(Project.findProjectsByMember(id));
        List<Project> list = new ArrayList<>();
        list.addAll(projects);
        Collections.sort(list, new Comparator<Project>() {
            @Override
            public int compare(Project lhs, Project rhs) {
                if(lhs.owner.compareToIgnoreCase(rhs.owner) == 0) {
                    return lhs.name.compareToIgnoreCase(rhs.name);
                }
                return lhs.owner.compareToIgnoreCase(rhs.owner);
            }
        });
        return list;
    }

    public boolean isLocked() {
        return this.state == UserState.LOCKED || this.state == UserState.DELETED;
    }

    public String getPureNameOnly(){
        String currentUserLanguage = StringUtils.defaultString(UserApp.currentUser().lang,
                "ko-KR");

        if (StringUtils.isNotBlank(englishName)
                && lang != null && currentUserLanguage.startsWith("en")) {
            return englishName;
        }

        String pureName = this.name;
        String[] spliters = {"[", "("};
        for (String spliter : spliters) {
            if (pureName == null) {
//              There exists fairly deep object refer in
//              partial_view_child.scala.html,
//              like a 'childIssue.assignee.user.getPureNameOnly'
//              It seems that it makes bug
//              Manually refreshing ORM object can be a workaround
                this.refresh(); // Fallback, because of Ebean bug
                pureName = this.name;
            }
            if (pureName.contains(spliter)) {
                pureName = this.name.substring(0, this.name.indexOf(spliter)).trim();
            }
        }

        return pureName;
    }

    public String getPureNameOnly(String targetLang){
        if (StringUtils.isNotBlank(englishName) && lang != null
                && StringUtils.isNotBlank(targetLang) && targetLang.startsWith("en")) {
            return englishName;
        }
        String pureName = this.name;
        String [] spliters = { "[", "(" };
        for(String spliter: spliters) {
            if(pureName.contains(spliter)){
                pureName = this.name.substring(0, this.name.indexOf(spliter));
            }
        }

        return pureName;
    }

    public String extractDepartmentPart(){
        String departmentName = this.name;
        String [] spliters = { "[", "(" };
        for(String spliter: spliters) {
            if(departmentName.contains(spliter)){
                departmentName = this.name.substring(this.name.indexOf(spliter));
            }
        }

        return departmentName;

    }

    public String getDisplayName(){
        if (UserApp.currentUser().isAnonymous()) {
            return name;
        }

        if (StringUtils.isNotBlank(englishName) && lang != null && UserApp.currentUser().lang.startsWith("en")) {
            return englishName + " " + extractDepartmentPart();
        } else {
            return name;
        }
    }

    public String getDisplayName(User forCurrentUser){
        if (StringUtils.isNotBlank(englishName) && lang != null && forCurrentUser.lang.startsWith("en")) {
            return englishName + " " + extractDepartmentPart();
        } else {
            return name;
        }
    }
}
