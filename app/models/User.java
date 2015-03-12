/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author Ahn Hyeok Jun
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package models;

import com.avaje.ebean.*;
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
import utils.JodaDateUtil;
import utils.ReservedWordsValidator;

import javax.persistence.*;
import javax.persistence.OrderBy;
import java.text.SimpleDateFormat;
import java.util.*;

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

    public static final String LOGIN_ID_PATTERN = "[a-zA-Z0-9-]+([_.][a-zA-Z0-9-]+)*";
    public static final String LOGIN_ID_PATTERN_ALLOW_FORWARD_SLASH = "[a-zA-Z0-9-/]+([_.][a-zA-Z0-9-/]+)*";

    public static final User anonymous = new NullUser();

    @Id
    public Long id;

    /**
     * name to show at web pages
     */
    public String name;

    @Pattern(value = "^" + LOGIN_ID_PATTERN + "$", message = "user.wrongloginId.alert")
    @Required
    @ValidateWith(ReservedWordsValidator.class)
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

    @Transient
    private Boolean siteManager;

    @Transient
    private Map<Long, Boolean> projectManagerMemo = new HashMap<>();

    @Transient
    private Map<Long, Boolean> projectMembersMemo = new HashMap<>();

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

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    public RecentlyVisitedProjects recentlyVisitedProjects;

    @OneToMany(mappedBy = "user")
    public List<Mention> mentions;

    /**
     * The user's preferred language code which can be recognized by {@link play.api.i18n.Lang#get},
     * such as "ko", "en-US" or "ja". This field is used as a language for notification mail.
     */
    public String lang;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    public List<OrganizationUser> organizationUsers;

    public User() {
    }

    public User(Long id) {
        this.id = id;
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

    /**
     * Create a user and set creation date
     *
     * @param user
     * @return user's id (not login id)
     */
    public static Long create(User user) {
        user.createdDate = JodaDateUtil.now();
        user.save();
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
        User user = find.where().ieq("loginId", loginId).findUnique();
        if (user == null) {
            return anonymous;
        }
        else {
            return user;
        }
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
        el.eq("state", state);

        if(StringUtils.isNotBlank(query)) {
            el = el.disjunction();
            el = el.icontains("loginId", query).icontains("name", query).icontains("email", query);
            el.endJunction();
        }

        return el.findPagingList(USER_COUNT_PER_PAGE).getPage(pageNum);
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
        user.password = new Sha256Hash(newPassword, ByteSource.Util.bytes(user.passwordSalt), 1024)
                .toBase64();
        user.save();
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
        if (!projectMembersMemo.containsKey(project.id)) {
            projectMembersMemo.put(project.id, ProjectUser.isMember(id, project.id));
        }

        return projectMembersMemo.get(project.id);
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
    }

    public void enroll(Organization organization) {
        getEnrolledOrganizations().add(organization);
        this.update();
    }

    /**
     * cancel enrol request at {@code project}
     *
     * @param project
     */
    public void cancelEnroll(Project project) {
        getEnrolledProjects().remove(project);
        this.update();
    }

    public void cancelEnroll(Organization organization) {
        getEnrolledOrganizations().remove(organization);
        this.update();
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
        super.delete();
    }

    public void changeState(UserState state) {
        this.state = state;
        lastStateModifiedDate = new Date();

        if (this.state == UserState.DELETED) {
            name = "DELETED";
            oldPassword = "";
            password = "";
            passwordSalt = "";
            email = "deleted-" + loginId + "@noreply.yobi.io";
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
    }

    public String avatarUrl() {
        Long avatarId = avatarId();
        if (avatarId == null) {
            return UserApp.DEFAULT_AVATAR_URL;
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

        if (!users.contains(currentUser)) {
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

        if (!users.contains(currentUser)) {
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
    }

    public void visits(Project project) {
        this.recentlyVisitedProjects = RecentlyVisitedProjects.addNewVisitation(this, project);
        this.update();
    }

    public List<ProjectVisitation> getVisitedProjects(int size) {
        if(size < 1 || this.recentlyVisitedProjects == null) {
            return new ArrayList<>();
        }

        return this.recentlyVisitedProjects.findRecentlyVisitedProjects(size);
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
        if(!(o instanceof User)) {
            return false;
        }
        if(o == this) {
            return true;
        }
        User user = (User) o;
        return this.id.equals(user.id) && this.loginId.equals(user.loginId);
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = result * 37 + (this.id != null ? this.id.hashCode() : 0);
      result = result * 37 + (this.loginId != null ? this.loginId.hashCode() : 0);
      return result;
    }
}
